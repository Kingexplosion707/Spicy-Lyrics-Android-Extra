/*
 * Spicy Lyrics for Flipper Zero.
 *
 * Shows the song and its synced lyrics as Spicy Lyrics Plus sends them, from the phone over
 * Bluetooth (the Flipper's serial profile) or from the PC over USB (the second serial port), and
 * turns the buttons into playback controls.
 *
 * The link is text, one message per line, fields split by tabs:
 *   from the app   ?                         hello; answered with "SPICY\t1"
 *                  S\t<title>\t<artist>      the song
 *                  L\t<prev>\t<line>\t<next> the lyric lines around now
 *                  P\t<0|1>\t<pos ms>\t<duration ms>  playing state, about once a second
 *                  N\t<text>                 a note in place of lyrics ("No lyrics", ...)
 *   to the app     C\t<play|next|prev>       a button
 */

#include <furi.h>
#include <furi_hal.h>
#include <furi_hal_usb_cdc.h>
#include <gui/gui.h>
#include <input/input.h>
#include <notification/notification_messages.h>
#include <bt/bt_service/bt.h>
#include <profiles/serial_profile.h>

#include <stdlib.h>
#include <string.h>

// Text is cut to fit the screen on purpose.
#pragma GCC diagnostic ignored "-Wformat-truncation"

#define TAG "SpicyLyrics"

#define TEXT_LEN      160
#define LINE_LEN      600
#define BT_BUFFER     1024
#define CDC_IF        1
#define LINK_TIMEOUT  6000
#define RECLAIM_MS    400

typedef enum {
    LinkNone,
    LinkBt,
    LinkUsb,
} Link;

typedef enum {
    LayoutFull, // song, line, next line, progress
    LayoutLyrics, // the line alone, as large as it fits
    LayoutCount,
} Layout;

typedef enum {
    WorkerStop = (1 << 0),
    WorkerBtRx = (1 << 1),
    WorkerUsbRx = (1 << 2),
} WorkerFlag;

typedef struct {
    char line[LINE_LEN];
    size_t len;
} LineBuffer;

typedef struct {
    // What the app sent.
    char title[TEXT_LEN];
    char artist[TEXT_LEN];
    char prev[TEXT_LEN];
    char cur[TEXT_LEN];
    char next[TEXT_LEN];
    char note[TEXT_LEN];
    bool playing;
    uint32_t pos_ms;
    uint32_t dur_ms;
    uint32_t pos_tick; // when pos_ms was true
    // The links.
    Link link; // the one the app last spoke on
    uint32_t last_rx_tick;
    bool bt_ok; // the Bluetooth profile is up
    bool bt_connected;
    bool usb_connected;
    Layout layout;
    uint32_t pressed_tick;
    const char* pressed;
} Model;

typedef struct {
    Model model;
    FuriMutex* mutex;
    FuriMessageQueue* input;
    ViewPort* view_port;
    Gui* gui;
    NotificationApp* notifications;

    FuriThread* worker;
    LineBuffer bt_line;
    LineBuffer usb_line;

    Bt* bt;
    FuriHalBleProfileBase* ble;
    FuriStreamBuffer* bt_rx;
    FuriTimer* reclaim;

    FuriHalUsbInterface* usb_prev;
    CdcCallbacks cdc;
} App;

// ---------------------------------------------------------------------------------------------
// Text

static void copy_field(char* dst, const char* src) {
    strncpy(dst, src ? src : "", TEXT_LEN - 1);
    dst[TEXT_LEN - 1] = '\0';
}

/** Splits [line] at tabs, in place. Returns the count of fields. */
static size_t split_fields(char* line, char** fields, size_t max) {
    size_t n = 0;
    char* p = line;
    while(n < max) {
        fields[n++] = p;
        char* tab = strchr(p, '\t');
        if(!tab) break;
        *tab = '\0';
        p = tab + 1;
    }
    return n;
}

/** Copies as much of [src] as fits [width] px, ending in "..." when cut. */
static void fit_text(Canvas* canvas, const char* src, char* dst, size_t dst_len, uint8_t width) {
    strncpy(dst, src, dst_len - 1);
    dst[dst_len - 1] = '\0';
    if(canvas_string_width(canvas, dst) <= width) return;
    size_t len = strlen(dst);
    while(len > 0) {
        dst[--len] = '\0';
        char tmp[TEXT_LEN + 4];
        snprintf(tmp, sizeof(tmp), "%s...", dst);
        if(canvas_string_width(canvas, tmp) <= width) {
            strncpy(dst, tmp, dst_len - 1);
            dst[dst_len - 1] = '\0';
            return;
        }
    }
}

/**
 * Word-wraps [text] into rows at most [width] px wide, drawing up to [max_rows] of them from
 * baseline [y] every [step] px. Returns the rows it needed (which may be more than it drew).
 */
static uint8_t draw_wrapped(
    Canvas* canvas,
    const char* text,
    uint8_t x,
    uint8_t y,
    uint8_t step,
    uint8_t width,
    uint8_t max_rows,
    bool center,
    bool draw) {
    char row[TEXT_LEN];
    char test[TEXT_LEN];
    uint8_t rows = 0;
    size_t row_len = 0;
    row[0] = '\0';
    const char* p = text;
    while(*p) {
        while(*p == ' ')
            p++;
        if(!*p) break;
        const char* end = p;
        while(*end && *end != ' ')
            end++;
        size_t word = end - p;
        if(word >= TEXT_LEN - 1) word = TEXT_LEN - 2;
        // Try the word on this row.
        if(row_len) {
            snprintf(test, sizeof(test), "%s %.*s", row, (int)word, p);
        } else {
            snprintf(test, sizeof(test), "%.*s", (int)word, p);
        }
        if(row_len == 0 || canvas_string_width(canvas, test) <= width) {
            strncpy(row, test, sizeof(row) - 1);
            row[sizeof(row) - 1] = '\0';
            row_len = strlen(row);
            p = end;
            continue;
        }
        // The row is full.
        if(draw && rows < max_rows) {
            if(rows == max_rows - 1) {
                // Last row there is room for: say the rest goes on.
                char cut[TEXT_LEN];
                snprintf(test, sizeof(test), "%s %s", row, p);
                fit_text(canvas, test, cut, sizeof(cut), width);
                canvas_draw_str_aligned(
                    canvas, center ? 64 : x, y + rows * step, center ? AlignCenter : AlignLeft, AlignBottom, cut);
                return rows + 2;
            }
            canvas_draw_str_aligned(
                canvas, center ? 64 : x, y + rows * step, center ? AlignCenter : AlignLeft, AlignBottom, row);
        }
        rows++;
        row_len = 0;
        row[0] = '\0';
    }
    if(row_len) {
        if(draw && rows < max_rows) {
            canvas_draw_str_aligned(
                canvas, center ? 64 : x, y + rows * step, center ? AlignCenter : AlignLeft, AlignBottom, row);
        }
        rows++;
    }
    return rows;
}

// ---------------------------------------------------------------------------------------------
// Drawing

static uint32_t position_now(const Model* m) {
    uint32_t pos = m->pos_ms;
    if(m->playing) pos += furi_get_tick() - m->pos_tick;
    if(m->dur_ms && pos > m->dur_ms) pos = m->dur_ms;
    return pos;
}

static void draw_waiting(Canvas* canvas, const Model* m) {
    canvas_set_font(canvas, FontPrimary);
    canvas_draw_str_aligned(canvas, 64, 12, AlignCenter, AlignBottom, "Spicy Lyrics");
    canvas_set_font(canvas, FontSecondary);
    canvas_draw_str_aligned(canvas, 64, 24, AlignCenter, AlignBottom, "Waiting for the app...");
    // Both apps: Settings > Device > Flipper Zero.
    canvas_draw_str(canvas, 2, 36, "Turn on in Spicy Lyrics:");
    canvas_draw_str(canvas, 2, 45, "Settings > Device > Flipper");
    canvas_draw_str(canvas, 2, 57, m->bt_ok ? "Phone: Bluetooth  PC: USB" : "Phone: Flipper Bluetooth is off");
}

static void draw_dots(Canvas* canvas, uint8_t y) {
    for(uint8_t i = 0; i < 3; i++)
        canvas_draw_disc(canvas, 52 + i * 12, y, 3);
}

static void draw_progress(Canvas* canvas, const Model* m) {
    if(!m->dur_ms) return;
    uint32_t pos = position_now(m);
    uint8_t w = (uint8_t)((uint64_t)pos * 124 / m->dur_ms);
    canvas_draw_frame(canvas, 2, 60, 124, 3);
    canvas_draw_box(canvas, 2, 60, w, 3);
    if(!m->playing) {
        // Paused: two bars by the progress.
        canvas_set_color(canvas, ColorWhite);
        canvas_draw_box(canvas, 118, 51, 9, 8);
        canvas_set_color(canvas, ColorBlack);
        canvas_draw_box(canvas, 120, 52, 2, 6);
        canvas_draw_box(canvas, 124, 52, 2, 6);
    }
}

static void draw_callback(Canvas* canvas, void* context) {
    App* app = context;
    furi_mutex_acquire(app->mutex, FuriWaitForever);
    Model* m = &app->model;
    canvas_clear(canvas);
    canvas_set_color(canvas, ColorBlack);

    bool live = m->link != LinkNone && (furi_get_tick() - m->last_rx_tick) < LINK_TIMEOUT;
    if(!live) {
        draw_waiting(canvas, m);
        furi_mutex_release(app->mutex);
        return;
    }

    const char* line = m->cur[0] ? m->cur : (m->note[0] ? m->note : "...");
    // A break in the singing: three dots, as the lyrics screen draws it.
    bool interlude = strcmp(line, "* * *") == 0;
    char buf[TEXT_LEN + 4];

    if(m->layout == LayoutFull) {
        // Song and link.
        canvas_set_font(canvas, FontSecondary);
        if(m->artist[0]) {
            snprintf(buf, sizeof(buf), "%s - %s", m->title, m->artist);
        } else {
            snprintf(buf, sizeof(buf), "%s", m->title);
        }
        const char* via = m->link == LinkBt ? "BT" : "USB";
        char top[TEXT_LEN];
        fit_text(canvas, buf, top, sizeof(top), 124 - canvas_string_width(canvas, via) - 4);
        canvas_draw_str(canvas, 1, 8, top);
        canvas_draw_str_aligned(canvas, 127, 8, AlignRight, AlignBottom, via);
        canvas_draw_line(canvas, 0, 10, 127, 10);

        // The line, in bold when it fits two rows, else smaller.
        canvas_set_font(canvas, FontPrimary);
        uint8_t rows = interlude ? 0 : draw_wrapped(canvas, line, 2, 23, 11, 124, 2, true, false);
        if(interlude) {
            draw_dots(canvas, 26);
        } else if(rows <= 2) {
            draw_wrapped(canvas, line, 2, 23, 11, 124, 2, true, true);
        } else {
            canvas_set_font(canvas, FontSecondary);
            draw_wrapped(canvas, line, 2, 20, 9, 124, 3, true, true);
        }
        // What comes next.
        if(m->next[0]) {
            canvas_set_font(canvas, FontSecondary);
            char next[TEXT_LEN];
            fit_text(canvas, m->next, next, sizeof(next), 112);
            canvas_draw_str_aligned(canvas, 64, 56, AlignCenter, AlignBottom, next);
        }
        draw_progress(canvas, m);
    } else {
        // The line alone: bold up to four rows, otherwise smaller.
        canvas_set_font(canvas, FontPrimary);
        uint8_t rows = interlude ? 0 : draw_wrapped(canvas, line, 2, 14, 13, 124, 4, true, false);
        if(interlude) {
            draw_dots(canvas, 30);
        } else if(rows <= 4) {
            uint8_t top = 14 + (4 - rows) * 13 / 2;
            draw_wrapped(canvas, line, 2, top, 13, 124, 4, true, true);
        } else {
            canvas_set_font(canvas, FontSecondary);
            draw_wrapped(canvas, line, 2, 10, 9, 124, 6, true, true);
        }
        draw_progress(canvas, m);
    }

    // A pressed button shows briefly.
    if(m->pressed && furi_get_tick() - m->pressed_tick < 700) {
        canvas_set_font(canvas, FontSecondary);
        uint8_t w = canvas_string_width(canvas, m->pressed) + 8;
        canvas_set_color(canvas, ColorWhite);
        canvas_draw_box(canvas, 64 - w / 2, 24, w, 14);
        canvas_set_color(canvas, ColorBlack);
        canvas_draw_rframe(canvas, 64 - w / 2, 24, w, 14, 3);
        canvas_draw_str_aligned(canvas, 64, 35, AlignCenter, AlignBottom, m->pressed);
    }
    furi_mutex_release(app->mutex);
}

static void input_callback(InputEvent* event, void* context) {
    App* app = context;
    furi_message_queue_put(app->input, event, FuriWaitForever);
}

// ---------------------------------------------------------------------------------------------
// The link

static void send_line(App* app, const char* text) {
    size_t len = strlen(text);
    furi_mutex_acquire(app->mutex, FuriWaitForever);
    Link link = app->model.link;
    bool bt = app->model.bt_connected;
    bool usb = app->model.usb_connected;
    furi_mutex_release(app->mutex);

    if((link == LinkBt || link == LinkNone) && bt && app->ble) {
        ble_profile_serial_tx(app->ble, (uint8_t*)text, len);
    }
    if((link == LinkUsb || link == LinkNone) && usb) {
        for(size_t off = 0; off < len; off += CDC_DATA_SZ) {
            size_t n = len - off > CDC_DATA_SZ ? CDC_DATA_SZ : len - off;
            furi_hal_cdc_send(CDC_IF, (uint8_t*)text + off, n);
            furi_delay_ms(2);
        }
    }
}

static void handle_message(App* app, char* line, Link from) {
    char* f[5] = {0};
    size_t n = split_fields(line, f, 5);
    if(n == 0 || !f[0][0]) return;

    if(f[0][0] == '?') {
        furi_mutex_acquire(app->mutex, FuriWaitForever);
        if(from == LinkBt) app->model.bt_connected = true;
        if(from == LinkUsb) app->model.usb_connected = true;
        app->model.link = from;
        app->model.last_rx_tick = furi_get_tick();
        furi_mutex_release(app->mutex);
        send_line(app, "SPICY\t1\n");
        return;
    }

    furi_mutex_acquire(app->mutex, FuriWaitForever);
    Model* m = &app->model;
    m->link = from;
    m->last_rx_tick = furi_get_tick();
    // Hearing from a link means it works, whatever its own signals said.
    if(from == LinkBt) m->bt_connected = true;
    if(from == LinkUsb) m->usb_connected = true;
    switch(f[0][0]) {
    case 'S':
        copy_field(m->title, n > 1 ? f[1] : "");
        copy_field(m->artist, n > 2 ? f[2] : "");
        m->prev[0] = m->cur[0] = m->next[0] = m->note[0] = '\0';
        break;
    case 'L':
        copy_field(m->prev, n > 1 ? f[1] : "");
        copy_field(m->cur, n > 2 ? f[2] : "");
        copy_field(m->next, n > 3 ? f[3] : "");
        m->note[0] = '\0';
        break;
    case 'P':
        m->playing = n > 1 && f[1][0] == '1';
        m->pos_ms = n > 2 ? (uint32_t)strtoul(f[2], NULL, 10) : 0;
        m->dur_ms = n > 3 ? (uint32_t)strtoul(f[3], NULL, 10) : 0;
        m->pos_tick = furi_get_tick();
        break;
    case 'N':
        copy_field(m->note, n > 1 ? f[1] : "");
        m->prev[0] = m->cur[0] = m->next[0] = '\0';
        break;
    default:
        break;
    }
    furi_mutex_release(app->mutex);
    view_port_update(app->view_port);
}

static void feed(App* app, LineBuffer* lb, const uint8_t* data, size_t len, Link from) {
    for(size_t i = 0; i < len; i++) {
        char c = (char)data[i];
        if(c == '\n' || c == '\r') {
            if(lb->len) {
                lb->line[lb->len] = '\0';
                handle_message(app, lb->line, from);
                lb->len = 0;
            }
        } else if(lb->len < LINE_LEN - 1) {
            lb->line[lb->len++] = c;
        }
    }
}

static int32_t worker_thread(void* context) {
    App* app = context;
    uint8_t buf[64];
    while(true) {
        uint32_t flags =
            furi_thread_flags_wait(WorkerStop | WorkerBtRx | WorkerUsbRx, FuriFlagWaitAny, 500);
        if(flags & FuriFlagError) flags = 0; // timeout
        if(flags & WorkerStop) break;
        if(flags & WorkerBtRx) {
            size_t n;
            while((n = furi_stream_buffer_receive(app->bt_rx, buf, sizeof(buf), 0)) > 0) {
                feed(app, &app->bt_line, buf, n, LinkBt);
            }
            if(app->ble) ble_profile_serial_notify_buffer_is_empty(app->ble);
        }
        if(flags & WorkerUsbRx) {
            int32_t n;
            while((n = furi_hal_cdc_receive(CDC_IF, buf, sizeof(buf))) > 0) {
                feed(app, &app->usb_line, buf, (size_t)n, LinkUsb);
            }
        }
    }
    return 0;
}

// Bluetooth

static uint16_t bt_serial_callback(SerialServiceEvent event, void* context) {
    App* app = context;
    if(event.event == SerialServiceEventTypeDataReceived) {
        furi_stream_buffer_send(app->bt_rx, event.data.buffer, event.data.size, 0);
        furi_thread_flags_set(furi_thread_get_id(app->worker), WorkerBtRx);
    }
    return (uint16_t)furi_stream_buffer_spaces_available(app->bt_rx);
}

/*
 * The Bluetooth service opens its own remote-control session on whatever connects to the
 * serial profile; take the channel back for the lyrics once it has.
 */
static void reclaim_callback(void* context) {
    App* app = context;
    if(!app->ble) return;
    ble_profile_serial_set_event_callback(app->ble, BT_BUFFER, bt_serial_callback, app);
    ble_profile_serial_set_rpc_active(app->ble, FuriHalBtSerialRpcStatusNotActive);
}

static void bt_status_callback(BtStatus status, void* context) {
    App* app = context;
    bool connected = status == BtStatusConnected;
    furi_mutex_acquire(app->mutex, FuriWaitForever);
    app->model.bt_connected = connected;
    if(!connected && app->model.link == LinkBt) app->model.link = LinkNone;
    furi_mutex_release(app->mutex);
    if(connected) furi_timer_start(app->reclaim, furi_ms_to_ticks(RECLAIM_MS));
    view_port_update(app->view_port);
}

static void bt_start(App* app) {
    app->bt = furi_record_open(RECORD_BT);
    bt_disconnect(app->bt);
    furi_delay_ms(200);
    app->ble = bt_profile_start(app->bt, ble_profile_serial, NULL);
    if(app->ble) {
        furi_hal_bt_start_advertising();
        ble_profile_serial_set_event_callback(app->ble, BT_BUFFER, bt_serial_callback, app);
        bt_set_status_changed_callback(app->bt, bt_status_callback, app);
    } else {
        FURI_LOG_W(TAG, "Bluetooth serial profile didn't start");
    }
    app->model.bt_ok = app->ble != NULL;
}

static void bt_stop(App* app) {
    furi_timer_stop(app->reclaim);
    bt_set_status_changed_callback(app->bt, NULL, NULL);
    if(app->ble) ble_profile_serial_set_event_callback(app->ble, 0, NULL, NULL);
    bt_disconnect(app->bt);
    furi_delay_ms(200);
    bt_profile_restore_default(app->bt);
    furi_record_close(RECORD_BT);
    app->bt = NULL;
    app->ble = NULL;
}

// USB

static void cdc_rx_callback(void* context) {
    App* app = context;
    furi_thread_flags_set(furi_thread_get_id(app->worker), WorkerUsbRx);
}

static void cdc_state_callback(void* context, CdcState state) {
    App* app = context;
    furi_mutex_acquire(app->mutex, FuriWaitForever);
    app->model.usb_connected = state == CdcStateConnected;
    if(state != CdcStateConnected && app->model.link == LinkUsb) app->model.link = LinkNone;
    furi_mutex_release(app->mutex);
}

static void cdc_ctrl_line_callback(void* context, CdcCtrlLine lines) {
    // The PC app opening the port raises DTR.
    App* app = context;
    furi_mutex_acquire(app->mutex, FuriWaitForever);
    if(lines & CdcCtrlLineDTR) app->model.usb_connected = true;
    furi_mutex_release(app->mutex);
}

static void usb_start(App* app) {
    app->usb_prev = furi_hal_usb_get_config();
    furi_hal_usb_unlock();
    if(app->usb_prev != &usb_cdc_dual && !furi_hal_usb_set_config(&usb_cdc_dual, NULL)) {
        FURI_LOG_W(TAG, "USB serial didn't start");
        app->usb_prev = NULL;
        return;
    }
    app->cdc.tx_ep_callback = NULL;
    app->cdc.rx_ep_callback = cdc_rx_callback;
    app->cdc.state_callback = cdc_state_callback;
    app->cdc.ctrl_line_callback = cdc_ctrl_line_callback;
    app->cdc.config_callback = NULL;
    furi_hal_cdc_set_callbacks(CDC_IF, &app->cdc, app);
    app->model.usb_connected = furi_hal_cdc_get_ctrl_line_state(CDC_IF) & CdcCtrlLineDTR;
}

static void usb_stop(App* app) {
    furi_hal_cdc_set_callbacks(CDC_IF, NULL, NULL);
    if(app->usb_prev && app->usb_prev != &usb_cdc_dual) {
        furi_hal_usb_set_config(app->usb_prev, NULL);
    }
}

// ---------------------------------------------------------------------------------------------

static void press(App* app, const char* label, const char* command) {
    furi_mutex_acquire(app->mutex, FuriWaitForever);
    app->model.pressed = label;
    app->model.pressed_tick = furi_get_tick();
    if(command[0] == 'p' && command[1] == 'l') {
        // Show the change at once; the app's next update confirms it.
        app->model.pos_ms = position_now(&app->model);
        app->model.pos_tick = furi_get_tick();
        app->model.playing = !app->model.playing;
    }
    furi_mutex_release(app->mutex);
    char line[24];
    snprintf(line, sizeof(line), "C\t%s\n", command);
    send_line(app, line);
    view_port_update(app->view_port);
}

int32_t spicy_lyrics_app(void* p) {
    UNUSED(p);
    App* app = malloc(sizeof(App));
    memset(app, 0, sizeof(App));
    app->mutex = furi_mutex_alloc(FuriMutexTypeNormal);
    app->input = furi_message_queue_alloc(8, sizeof(InputEvent));
    app->bt_rx = furi_stream_buffer_alloc(BT_BUFFER, 1);
    app->reclaim = furi_timer_alloc(reclaim_callback, FuriTimerTypeOnce, app);

    app->view_port = view_port_alloc();
    view_port_draw_callback_set(app->view_port, draw_callback, app);
    view_port_input_callback_set(app->view_port, input_callback, app);
    app->gui = furi_record_open(RECORD_GUI);
    gui_add_view_port(app->gui, app->view_port, GuiLayerFullscreen);

    // Lyrics are for reading: keep the screen lit while the app is open.
    app->notifications = furi_record_open(RECORD_NOTIFICATION);
    notification_message(app->notifications, &sequence_display_backlight_enforce_on);

    app->worker = furi_thread_alloc_ex("SpicyLyricsWorker", 2048, worker_thread, app);
    furi_thread_start(app->worker);

    bt_start(app);
    usb_start(app);

    InputEvent event;
    bool running = true;
    while(running) {
        if(furi_message_queue_get(app->input, &event, 250) == FuriStatusOk) {
            if(event.type == InputTypeShort) {
                switch(event.key) {
                case InputKeyOk:
                    press(app, app->model.playing ? "Pause" : "Play", "play");
                    break;
                case InputKeyRight:
                    press(app, "Next", "next");
                    break;
                case InputKeyLeft:
                    press(app, "Previous", "prev");
                    break;
                case InputKeyUp:
                case InputKeyDown:
                    furi_mutex_acquire(app->mutex, FuriWaitForever);
                    app->model.layout = (app->model.layout + 1) % LayoutCount;
                    furi_mutex_release(app->mutex);
                    break;
                case InputKeyBack:
                    running = false;
                    break;
                default:
                    break;
                }
            } else if(event.type == InputTypeLong && event.key == InputKeyBack) {
                running = false;
            }
        }
        // The progress moves, and a silent link turns back to waiting.
        view_port_update(app->view_port);
    }

    usb_stop(app);
    bt_stop(app);

    furi_thread_flags_set(furi_thread_get_id(app->worker), WorkerStop);
    furi_thread_join(app->worker);
    furi_thread_free(app->worker);

    notification_message(app->notifications, &sequence_display_backlight_enforce_auto);
    furi_record_close(RECORD_NOTIFICATION);

    gui_remove_view_port(app->gui, app->view_port);
    view_port_free(app->view_port);
    furi_record_close(RECORD_GUI);

    furi_timer_free(app->reclaim);
    furi_stream_buffer_free(app->bt_rx);
    furi_message_queue_free(app->input);
    furi_mutex_free(app->mutex);
    free(app);
    return 0;
}
