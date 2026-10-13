Запуск Сервера: java Main port \n
Запуск Клиента:  java Main 192.168.0.149 port file_path

Пример:
Сервер:
PS C:\Users\marti\Desktop\NetLabs\lab2\lab2\target\classes> java Main 8000                                             
Server is running on host with IP 192.168.0.149
Listening on port 8000
[#1 192.168.0.149:59757] Client connected
[#1 192.168.0.149:59757] fileNameStr: NAME:test.txt
[#1 192.168.0.149:59757] fileSizeStr: SIZE:12
[#1 192.168.0.149:59757] Receiving file: test.txt (12 bytes)
[#1 192.168.0.149:59757] Saving to: C:\Users\marti\Desktop\NetLabs\lab2\lab2\target\classes\uploads\test (1).txt
[#1 192.168.0.149:59757] File saved! Received bytes: 12, Total time: 0,00 s, Average: 0,01 MB/s (5,86 KB/s)
[#1 192.168.0.149:59757] Size check OK: 12 == 12
Клиент:
PS C:\Users\marti\Desktop\NetLabs\lab2\lab2\target\classes> java Main 192.168.0.149 8000 C:\Users\marti\Desktop\NetLabs\lab2\lab2\src\main\resources\test.txt
Connected to server!
Sending file: test.txt (12 bytes)
Headers sent, starting file transfer...
File sent, waiting for server response...
Server confirmed: file received successfully.
