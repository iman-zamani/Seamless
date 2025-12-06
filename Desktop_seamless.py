import customtkinter as ctk
import socket
import threading
import os
from tkinter import filedialog, messagebox
import time

# --- CONFIGURATION ---
UDP_PORT = 5000
TCP_PORT = 5001
BUFFER_SIZE = 1024 * 64  # Increased to 64KB for faster large file transfers
SEPARATOR = "<SEPARATOR>"

ctk.set_appearance_mode("Dark")
ctk.set_default_color_theme("dark-blue")

class SeamlessApp(ctk.CTk):
    def __init__(self):
        super().__init__()
        self.title("Seamless Desktop")
        self.geometry("600x550")
        
        # State
        self.username = f"User_{os.getpid()}"
        self.my_ip = self.get_local_ip()
        self.peers = {} 
        self.selected_files = []
        self.server_running = False

        # --- UI LAYOUT ---
        self.grid_columnconfigure(0, weight=1)
        self.grid_rowconfigure(1, weight=1)

        # Header
        self.header_frame = ctk.CTkFrame(self, fg_color="transparent")
        self.header_frame.grid(row=0, column=0, padx=20, pady=20, sticky="ew")
        
        self.lbl_title = ctk.CTkLabel(self.header_frame, text="Seamless", font=("Roboto Medium", 28))
        self.lbl_title.pack(side="left", padx=10)
        
        self.entry_username = ctk.CTkEntry(self.header_frame, width=150, placeholder_text="Username")
        self.entry_username.insert(0, self.username)
        self.entry_username.pack(side="right", padx=10)
        
        self.btn_update_user = ctk.CTkButton(self.header_frame, text="Set", width=50, command=self.update_username)
        self.btn_update_user.pack(side="right")

        # Main Content Area
        self.main_frame = ctk.CTkFrame(self, corner_radius=10)
        self.main_frame.grid(row=1, column=0, padx=20, pady=(0, 20), sticky="nsew")

        self.show_menu()
        threading.Thread(target=self.udp_listener, daemon=True).start()

    def get_local_ip(self):
        try:
            s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
            s.connect(("8.8.8.8", 80))
            ip = s.getsockname()[0]
            s.close()
            return ip
        except:
            return "127.0.0.1"

    def update_username(self):
        self.username = self.entry_username.get()
        messagebox.showinfo("Info", f"Username updated to {self.username}")

    def clear_main_frame(self):
        for widget in self.main_frame.winfo_children():
            widget.destroy()

    # --- VIEWS ---
    def show_menu(self):
        self.clear_main_frame()
        self.server_running = False
        
        btn_send = ctk.CTkButton(self.main_frame, text="SEND FILES", width=220, height=60, font=("Arial", 16, "bold"), command=self.show_send_ui)
        btn_send.pack(pady=(60, 20))
        
        btn_receive = ctk.CTkButton(self.main_frame, text="RECEIVE FILES", width=220, height=60, font=("Arial", 16, "bold"), fg_color="#2CC985", hover_color="#229A65", command=self.show_receive_ui)
        btn_receive.pack(pady=20)

    def show_send_ui(self):
        self.clear_main_frame()
        
        btn_select = ctk.CTkButton(self.main_frame, text="Choose Files", command=self.select_files)
        btn_select.pack(pady=20)

        lbl_info = ctk.CTkLabel(self.main_frame, text="Selected Files:", font=("Arial", 14))
        lbl_info.pack(pady=(0, 5))
        
        self.file_list_scroll = ctk.CTkScrollableFrame(self.main_frame, height=100)
        self.file_list_scroll.pack(fill="x", padx=20)

        # Scan Button
        btn_scan = ctk.CTkButton(self.main_frame, text="Scan Network", command=self.scan_network)
        btn_scan.pack(pady=20)

        lbl_dev = ctk.CTkLabel(self.main_frame, text="Available Devices:", font=("Arial", 14))
        lbl_dev.pack(pady=(0, 5))

        self.device_list_frame = ctk.CTkScrollableFrame(self.main_frame)
        self.device_list_frame.pack(fill="both", expand=True, padx=20, pady=(0, 20))
        
        btn_back = ctk.CTkButton(self.main_frame, text="Back", fg_color="transparent", border_width=1, command=self.show_menu)
        btn_back.pack(pady=10)

    def select_files(self):
        files = filedialog.askopenfilenames()
        if files:
            self.selected_files = files
            for widget in self.file_list_scroll.winfo_children():
                widget.destroy()
            for f in files:
                l = ctk.CTkLabel(self.file_list_scroll, text=os.path.basename(f))
                l.pack(anchor="w")

    def show_receive_ui(self):
        self.clear_main_frame()
        self.server_running = True
        
        lbl = ctk.CTkLabel(self.main_frame, text="Ready to Receive", font=("Arial", 20, "bold"))
        lbl.pack(pady=(30, 10))
        
        lbl_sub = ctk.CTkLabel(self.main_frame, text=f"Visible as: {self.username}\nIP: {self.my_ip}", text_color="gray")
        lbl_sub.pack(pady=5)

        # Progress Bar
        self.progress_bar = ctk.CTkProgressBar(self.main_frame, width=400)
        self.progress_bar.set(0)
        self.progress_bar.pack(pady=20)
        
        self.lbl_status = ctk.CTkLabel(self.main_frame, text="Waiting...", font=("Arial", 14))
        self.lbl_status.pack()

        self.log_box = ctk.CTkTextbox(self.main_frame, height=150)
        self.log_box.pack(fill="both", expand=True, padx=20, pady=20)
        
        btn_back = ctk.CTkButton(self.main_frame, text="Stop & Back", fg_color="#C0392B", hover_color="#922B21", command=self.show_menu)
        btn_back.pack(pady=10)
        
        threading.Thread(target=self.udp_broadcaster, daemon=True).start()
        threading.Thread(target=self.tcp_server, daemon=True).start()

    # --- NETWORKING ---

    def udp_listener(self):
        sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        sock.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
        
        # Bind to 0.0.0.0 to catch ALL traffic
        try:
            sock.bind(('0.0.0.0', UDP_PORT))
        except Exception as e:
            print(f"Failed to bind UDP: {e}")
            return

        print(f"DEBUG: Listening for UDP on port {UDP_PORT}")
        
        while True:
            try:
                data, addr = sock.recvfrom(1024)
                msg = data.decode()
                
                # --- DEBUG PRINT ---
                print(f"DEBUG: Packet received from {addr}: {msg}")
                # -------------------

                if msg.startswith("HERE:"):
                    name = msg.split(":")[1]
                    # Allow self-discovery for testing, or filter if needed
                    self.peers[addr[0]] = name
                    self.after(0, self.update_peer_list)
                        
                elif msg.startswith("DISCOVER"):
                    # Reply to discovery
                    if self.server_running:
                        rs = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
                        rs.sendto(f"HERE:{self.username}".encode(), (addr[0], UDP_PORT))
            except Exception as e:
                print(f"UDP Loop Error: {e}")

    def udp_broadcaster(self):
        sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        sock.setsockopt(socket.SOL_SOCKET, socket.SO_BROADCAST, 1)
        while self.server_running:
            try:
                sock.sendto(f"HERE:{self.username}".encode(), ('<broadcast>', UDP_PORT))
                time.sleep(2)
            except: break

    def scan_network(self):
        self.peers = {}
        try:
            sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
            sock.setsockopt(socket.SOL_SOCKET, socket.SO_BROADCAST, 1)
            sock.sendto(f"DISCOVER:{self.username}".encode(), ('<broadcast>', UDP_PORT))
        except Exception as e:
            messagebox.showerror("Network Error", str(e))

    def update_peer_list(self):
        if hasattr(self, 'device_list_frame') and self.device_list_frame.winfo_exists():
            for w in self.device_list_frame.winfo_children(): w.destroy()
            for ip, name in self.peers.items():
                ctk.CTkButton(self.device_list_frame, text=f"{name} ({ip})", 
                              command=lambda i=ip: self.send_files(i)).pack(pady=5, fill="x")

    def send_files(self, target_ip):
        if not self.selected_files: return
        try:
            for filepath in self.selected_files:
                filesize = os.path.getsize(filepath)
                filename = os.path.basename(filepath)
                
                s = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
                s.connect((target_ip, TCP_PORT))
                s.send(f"{filename}{SEPARATOR}{filesize}\n".encode())
                
                with open(filepath, "rb") as f:
                    while True:
                        bytes_read = f.read(BUFFER_SIZE)
                        if not bytes_read: break
                        s.sendall(bytes_read)
                s.close()
            messagebox.showinfo("Success", "Files sent!")
            self.show_menu()
        except Exception as e:
            messagebox.showerror("Error", str(e))

    def tcp_server(self):
        server_socket = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        server_socket.bind(("0.0.0.0", TCP_PORT))
        server_socket.listen(5)
        while self.server_running:
            try:
                client, _ = server_socket.accept()
                threading.Thread(target=self.handle_incoming_file, args=(client,)).start()
            except: break

    def handle_incoming_file(self, client_socket):
        try:
            # Read header
            header_bytes = b""
            while True:
                b = client_socket.recv(1)
                if b == b'\n': break
                header_bytes += b
            
            header = header_bytes.decode()
            filename, filesize = header.split(SEPARATOR)
            filesize = int(filesize)
            
            self.log_box.insert("end", f"Start: {filename} ({filesize/1024/1024:.2f} MB)\n")
            self.progress_bar.set(0)
            
            save_path = os.path.join(os.getcwd(), f"received_{filename}")
            
            received_total = 0
            with open(save_path, "wb") as f:
                while received_total < filesize:
                    bytes_read = client_socket.recv(BUFFER_SIZE)
                    if not bytes_read: break
                    f.write(bytes_read)
                    received_total += len(bytes_read)
                    
                    # Update Progress (Throttle updates to prevent freeze)
                    progress = received_total / filesize
                    self.progress_bar.set(progress)
                    self.lbl_status.configure(text=f"Receiving: {int(progress*100)}%")
                    self.update_idletasks() # Keep UI responsive

            self.log_box.insert("end", f"Saved: {save_path}\n")
            self.lbl_status.configure(text="Transfer Complete")
            client_socket.close()
        except Exception as e:
            print(f"Error: {e}")

if __name__ == "__main__":
    app = SeamlessApp()
    app.mainloop()
