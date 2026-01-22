import socket

def send_sql(sql):
    try:
        with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as s:
            s.connect(("127.0.0.1", 7778))
            s.sendall(sql.encode() + b'\0')
            
            data = b""
            while True:
                chunk = s.recv(1024)
                if not chunk: break
                data += chunk
                if b"\0" in data: break
            
            print(f"Sent: {sql}")
            print(f"Received: {data.decode().strip(chr(0))}")
            print("-" * 30)
    except ConnectionRefusedError:
        print("Error: Server is not running!")

# 1. Create a User (Write Command)
send_sql("INSERT INTO users (username, password, created_at) VALUES ('neo', 'trinity123', '2023-10-27')")

# 2. Check if the user exists (Read Query)
send_sql("SELECT * FROM users WHERE username='neo'")