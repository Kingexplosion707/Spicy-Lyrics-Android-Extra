' Starts the Spicy Lyrics relay without a window (it exits by itself if one already runs).
Set fso = CreateObject("Scripting.FileSystemObject")
root = fso.GetParentFolderName(WScript.ScriptFullName)
Set sh = CreateObject("WScript.Shell")
sh.Environment("Process")("SLP_APP_DIR") = root & "\app"
sh.CurrentDirectory = root & "\relay"
sh.Run """" & root & "\node\node.exe"" """ & root & "\relay\bridge-server.js""", 0, False
