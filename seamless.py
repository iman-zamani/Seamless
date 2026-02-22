# seamless - High-performance cross-platform file transfer utility.
# Copyright (C) 2025 Iman Zamani
#
# This program is free software: you can redistribute it and/or modify
# it under the terms of the GNU General Public License as published by
# the Free Software Foundation, either version 3 of the License, or
# (at your option) any later version.
#
# This program is distributed in the hope that it will be useful,
# but WITHOUT ANY WARRANTY; without even the implied warranty of
# MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
# GNU General Public License for more details.
#
# You should have received a copy of the GNU General Public License
# along with this program. If not, see <https://www.gnu.org/licenses/>.

import customtkinter as ctk
import socket
import threading
import os
from tkinter import filedialog, messagebox
import time
from pathlib import Path

UDP_PORT = 5000
TCP_PORT = 5001
BUFFER_SIZE = 1024 * 64
SEPARATOR = "<SEPARATOR>"

# --- FUTURISTIC THEME COLORS ---
BG_COLOR = "#050505"
FRAME_COLOR = "#0A0A0A"
PRIMARY_PURPLE = "#7000FF"
HOVER_PURPLE = "#A200FF"
TEXT_COLOR = "#FFFFFF"
MUTED_TEXT = "#888888"

ctk.set_appearance_mode("Dark")

class CircularProgress(ctk.CTkCanvas):
    """Custom circular progress bar for individual file tracking."""
    def __init__(self, parent, size=26, bg_color=FRAME_COLOR, **kwargs):
        super().__init__(parent, width=size, height=size, bg=bg_color, highlightthickness=0, **kwargs)
        self.size = size
        self.set(0)

    def set(self, progress):
        self.delete("all")
        pad = 2
        if progress >= 1.0:
            # Draw a full outer ring
            self.create_oval(pad, pad, self.size-pad, self.size-pad, outline=HOVER_PURPLE, width=3)
            # Draw a checkmark inside
            scale = self.size / 24.0
            self.create_line(6*scale, 12*scale, 10*scale, 16*scale, fill=HOVER_PURPLE, width=3, capstyle="round", joinstyle="round")
            self.create_line(10*scale, 16*scale, 18*scale, 8*scale, fill=HOVER_PURPLE, width=3, capstyle="round", joinstyle="round")
        else:
            # Dark background ring
            self.create_oval(pad, pad, self.size-pad, self.size-pad, outline="#222222", width=3)
            # Bright purple progress ring
            angle = int(360 * progress)
            if angle > 0:
                self.create_arc(pad, pad, self.size-pad, self.size-pad, start=90, extent=-angle, 
                                outline=HOVER_PURPLE, width=3, style="arc")


class SeamlessApp(ctk.CTk):
    def __init__(self):
        super().__init__()
        self.title("Seamless Desktop")
        self.geometry("650x650")
        self.configure(fg_color=BG_COLOR)
        
        self.username = f"User_{os.getpid()}"
        self.peers = {} 
        self.selected_files = []
        self.server_running = False
        self.current_state = "menu" # Tracks where the back button should go
        self.cancel_transfer = False # Flag for mid-transfer cancellation
        self.active_socket = None
        self.active_receive_sockets = []
        # --- HEADER & NAVIGATION ---
        self.header_frame = ctk.CTkFrame(self, fg_color="transparent")
        self.header_frame.pack(fill="x", padx=20, pady=(20, 0))
        
        self.btn_back = ctk.CTkButton(self.header_frame, text="← Back", width=60, 
                                      fg_color="transparent", hover_color="#1A1A1A",
                                      text_color=MUTED_TEXT, font=("Arial", 14, "bold"),
                                      command=self.handle_back_button)
        self.btn_back.pack(side="left")
        self.btn_back.pack_forget() 

        self.lbl_title = ctk.CTkLabel(self.header_frame, text="SEAMLESS", 
                                      font=("Arial Black", 24), text_color=PRIMARY_PURPLE)
        self.lbl_title.pack(side="left", padx=20)
        
        self.btn_update_user = ctk.CTkButton(self.header_frame, text="Set", width=40, 
                                             fg_color=PRIMARY_PURPLE, hover_color=HOVER_PURPLE,
                                             command=self.update_username)
        self.btn_update_user.pack(side="right")

        self.entry_username = ctk.CTkEntry(self.header_frame, width=130, 
                                           fg_color="#111111", border_color=PRIMARY_PURPLE,
                                           placeholder_text="Username")
        self.entry_username.insert(0, self.username)
        self.entry_username.pack(side="right", padx=10)
        
        # --- MAIN CONTENT AREA ---
        self.main_frame = ctk.CTkFrame(self, fg_color=FRAME_COLOR, corner_radius=15)
        self.main_frame.pack(fill="both", expand=True, padx=20, pady=20)

        # Start background UDP listener for discovery
        threading.Thread(target=self.udp_listener, daemon=True).start()
        
        self.show_menu()

    # --- NETWORKING HELPERS ---
    def get_local_ip(self):
        try:
            s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
            s.connect(("10.255.255.255", 1))
            ip = s.getsockname()[0]
            s.close()
            return ip
        except:
            return "127.0.0.1"

    def get_all_interfaces(self):
        try:
            return socket.gethostbyname_ex(socket.gethostname())[2]
        except:
            return [self.get_local_ip()]

    def send_broadcast_packet(self, message):
        interfaces = self.get_all_interfaces()
        for ip in interfaces:
            if ip.startswith("127."): 
                continue
            try:
                with socket.socket(socket.AF_INET, socket.SOCK_DGRAM) as s:
                    s.setsockopt(socket.SOL_SOCKET, socket.SO_BROADCAST, 1)
                    s.bind((ip, 0)) 
                    s.sendto(message, ('255.255.255.255', UDP_PORT))
            except:
                pass

    def update_username(self):
        self.username = self.entry_username.get()
        messagebox.showinfo("Info", f"Username updated to {self.username}")

    # --- UI NAVIGATION & VIEWS ---
    def clear_main_frame(self):
        for widget in self.main_frame.winfo_children():
            widget.destroy()

    def handle_back_button(self):
        if self.current_state == "select_device":
            self.show_select_files_ui()
        elif self.current_state == "sending":
            self.confirm_cancel()
        elif self.current_state == "receive":
            self.cancel_receiving()
        else:
            self.show_menu()

    def cancel_receiving(self):
        # If there are active transfers, ask for confirmation
        if self.active_receive_sockets:
            confirm = messagebox.askyesno("Cancel Receiving", 
                                          "Files are currently being received.\nAre you sure you want to cancel?")
            if not confirm:
                return
        
        # Shut down the server flag
        self.server_running = False
        
        # Force close all active client sockets to break any blocked recv() calls
        for sock in self.active_receive_sockets:
            try:
                sock.close()
            except Exception:
                pass
        self.active_receive_sockets.clear()
        
        self.show_menu()

    def update_navigation(self, state):
        self.current_state = state
        if state == "menu":
            self.btn_back.pack_forget() # Hide back button on main menu
        else:
            self.btn_back.pack(side="left", before=self.lbl_title) # Show on all other pages

    def show_menu(self):
        self.clear_main_frame()
        self.server_running = False
        self.cancel_transfer = False
        self.update_navigation("menu")
        
        lbl_welcome = ctk.CTkLabel(self.main_frame, text="What would you like to do?", font=("Arial", 18))
        lbl_welcome.pack(pady=(60, 40))

        btn_send = ctk.CTkButton(self.main_frame, text="SEND FILES", width=250, height=70, 
                                 font=("Arial", 16, "bold"), fg_color=PRIMARY_PURPLE, 
                                 hover_color=HOVER_PURPLE, command=self.show_select_files_ui)
        btn_send.pack(pady=10)
        
        btn_receive = ctk.CTkButton(self.main_frame, text="RECEIVE FILES", width=250, height=70, 
                                    font=("Arial", 16, "bold"), fg_color="transparent", 
                                    border_width=2, border_color=PRIMARY_PURPLE, hover_color="#1A1A1A",
                                    command=self.show_receive_ui)
        btn_receive.pack(pady=20)

    # --- SENDING WORKFLOW ---
    # Page 1: Select Files
    def show_select_files_ui(self):
        self.clear_main_frame()
        self.update_navigation("select_files")
        
        lbl_title = ctk.CTkLabel(self.main_frame, text="STEP 1: Choose Files", font=("Arial", 20, "bold"))
        lbl_title.pack(pady=(20, 10))

        btn_select = ctk.CTkButton(self.main_frame, text="+ Browse Files", 
                                   fg_color="#222222", hover_color="#333333", command=self.select_files)
        btn_select.pack(pady=10)

        self.file_list_scroll = ctk.CTkScrollableFrame(self.main_frame, fg_color="#111111")
        self.file_list_scroll.pack(fill="both", expand=True, padx=30, pady=10)
        
        self.btn_next = ctk.CTkButton(self.main_frame, text="Next: Select Device →", height=40,
                                      fg_color=PRIMARY_PURPLE, hover_color=HOVER_PURPLE, 
                                      state="disabled", command=self.show_select_device_ui)
        self.btn_next.pack(pady=20, padx=30, fill="x")

        self.refresh_file_list_ui()

    def select_files(self):
        files = filedialog.askopenfilenames()
        if files:
            self.selected_files = list(files)
            self.refresh_file_list_ui()

    def refresh_file_list_ui(self):
        for widget in self.file_list_scroll.winfo_children():
            widget.destroy()
            
        if self.selected_files:
            self.btn_next.configure(state="normal")
            for f in self.selected_files:
                filename = os.path.basename(f)
                filesize = os.path.getsize(f) / (1024 * 1024)
                lbl = ctk.CTkLabel(self.file_list_scroll, text=f"📄 {filename} ({filesize:.2f} MB)", font=("Arial", 14))
                lbl.pack(anchor="w", pady=2)
        else:
            self.btn_next.configure(state="disabled")

    # Page 2: Select Device
    def show_select_device_ui(self):
        self.clear_main_frame()
        self.update_navigation("select_device")
        
        lbl_title = ctk.CTkLabel(self.main_frame, text="STEP 2: Select Destination", font=("Arial", 20, "bold"))
        lbl_title.pack(pady=(20, 10))

        btn_scan = ctk.CTkButton(self.main_frame, text="↻ Refresh Network Scan", 
                                 fg_color="#222222", hover_color="#333333", command=self.scan_network)
        btn_scan.pack(pady=10)

        self.device_list_frame = ctk.CTkScrollableFrame(self.main_frame, fg_color="#111111")
        self.device_list_frame.pack(fill="both", expand=True, padx=30, pady=10)
        
        self.scan_network()

    # Page 3: Sending Progress
    def show_sending_progress_ui(self, target_ip, target_name):
        self.clear_main_frame()
        self.update_navigation("sending")
        self.cancel_transfer = False # Reset flag when entering page
        
        lbl_title = ctk.CTkLabel(self.main_frame, text=f"Sending to {target_name}...", font=("Arial", 20, "bold"))
        lbl_title.pack(pady=(20, 10))

        # Overall Progress
        self.lbl_overall = ctk.CTkLabel(self.main_frame, text="Total Progress: 0%", font=("Arial", 14), text_color=MUTED_TEXT)
        self.lbl_overall.pack(pady=(5, 0))
        
        self.overall_progress_bar = ctk.CTkProgressBar(self.main_frame, width=400, height=10, 
                                                       progress_color=PRIMARY_PURPLE, fg_color="#222222")
        self.overall_progress_bar.set(0)
        self.overall_progress_bar.pack(pady=10)

        # Individual Files Frame
        self.progress_scroll = ctk.CTkScrollableFrame(self.main_frame, fg_color="#111111")
        self.progress_scroll.pack(fill="both", expand=True, padx=30, pady=10)

        self.file_progress_widgets = {}

        for f in self.selected_files:
            row_frame = ctk.CTkFrame(self.progress_scroll, fg_color="transparent")
            row_frame.pack(fill="x", pady=5)
            
            # Use Grid geometry manager to strictly isolate UI elements into uncrossable columns
            row_frame.grid_columnconfigure(0, weight=0, minsize=40) # Locks the circle's cell width
            row_frame.grid_columnconfigure(1, weight=1) # Allows the text to take remaining space
            
            # Column 0: Circular Progress Bar
            circ_prog = CircularProgress(row_frame, size=26, bg_color="#111111")
            circ_prog.grid(row=0, column=0, padx=(5, 5), sticky="w")
            self.file_progress_widgets[f] = circ_prog

            # Column 1: Filename text
            filename = os.path.basename(f)
            lbl = ctk.CTkLabel(row_frame, text=filename, font=("Arial", 14), anchor="w", justify="left")
            lbl.grid(row=0, column=1, padx=(0, 10), sticky="w")

        # Cancel Button
        self.btn_cancel = ctk.CTkButton(self.main_frame, text="Cancel Transfer", fg_color="#C0392B", 
                                        hover_color="#922B21", font=("Arial", 14, "bold"), 
                                        command=self.confirm_cancel)
        self.btn_cancel.pack(pady=(10, 20))
            
        # Start thread
        threading.Thread(target=self.process_send_files, args=(target_ip,), daemon=True).start()

    def confirm_cancel(self):
        confirm = messagebox.askyesno("Cancel Transfer", 
                                      "Are you sure you want to abort?\nPartial files may remain on the receiver's device.")
        if confirm:
            self.cancel_transfer = True
            self.btn_cancel.configure(state="disabled", text="Cancelling...")
            
            # kill the active socket to break any blocked network calls
            if hasattr(self, 'active_socket') and self.active_socket:
                try:
                    self.active_socket.close()
                except Exception:
                    pass

    # --- SENDING LOGIC (THREADED) ---
    def process_send_files(self, target_ip):
        try:
            total_bytes_to_send = sum(os.path.getsize(f) for f in self.selected_files)
            total_bytes_sent = 0

            for filepath in self.selected_files:
                if self.cancel_transfer:
                    break
                
                filesize = os.path.getsize(filepath)
                filename = os.path.basename(filepath)
                
                s = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
                self.active_socket = s    # Store reference for the cancel button
                s.settimeout(10.0)        # Timeout if receiver stops responding for 10s
                
                s.connect((target_ip, TCP_PORT))
                s.send(f"{filename}{SEPARATOR}{filesize}\n".encode())
                
                file_bytes_sent = 0
                circ_widget = self.file_progress_widgets[filepath]

                with open(filepath, "rb") as f:
                    while True:
                        if self.cancel_transfer:
                            break
                        
                        bytes_read = f.read(BUFFER_SIZE)
                        if not bytes_read: break
                        s.sendall(bytes_read)
                        
                        file_bytes_sent += len(bytes_read)
                        total_bytes_sent += len(bytes_read)
                        
                        file_prog = file_bytes_sent / filesize
                        overall_prog = total_bytes_sent / total_bytes_to_send
                        
                        self.after(0, circ_widget.set, file_prog)
                        self.after(0, self.overall_progress_bar.set, overall_prog)
                        self.after(0, lambda txt=f"Total Progress: {int(overall_prog*100)}%": self.lbl_overall.configure(text=txt)) 

                s.close()
                if not self.cancel_transfer:
                    self.after(0, circ_widget.set, 1.0) 

            if self.cancel_transfer:
                self.after(0, self.show_cancelled_and_return)
            else:
                self.after(0, self.show_success_and_return)

        except Exception as e:
            # If the user clicked cancel, closing the socket caused this exception. Let it end gracefully.
            if self.cancel_transfer:
                self.after(0, self.show_cancelled_and_return)
            else:
                # If it was a genuine network drop/timeout, show the error
                self.after(0, lambda err=e: messagebox.showerror("Transfer Error", f"Connection lost: {err}"))
                self.after(0, self.show_menu)

    def show_success_and_return(self):
        messagebox.showinfo("Success", "All files sent successfully!")
        self.selected_files = [] # Clear queue
        self.show_menu()
        
    def show_cancelled_and_return(self):
        messagebox.showinfo("Cancelled", "File transfer was aborted.")
        self.selected_files = []
        self.show_menu()

    # --- RECEIVING WORKFLOW ---
    def show_receive_ui(self):
        self.clear_main_frame()
        self.update_navigation("receive")
        self.server_running = True
        
        lbl_anim = ctk.CTkLabel(self.main_frame, text="📡", font=("Arial", 40))
        lbl_anim.pack(pady=(20, 0))

        lbl = ctk.CTkLabel(self.main_frame, text="Awaiting Transmissions...", font=("Arial", 20, "bold"), text_color=PRIMARY_PURPLE)
        lbl.pack(pady=(5, 10))
        
        # Display all local IPs nicely
        ips = [ip for ip in self.get_all_interfaces() if not ip.startswith("127.")]
        if not ips: ips = ["127.0.0.1 (Local Only)"]
        ip_display = "\n".join([f"• {ip}" for ip in ips])

        lbl_sub = ctk.CTkLabel(self.main_frame, text=f"Visible as: {self.username}\n\nYour IP Addresses:\n{ip_display}", 
                               text_color=MUTED_TEXT, justify="center")
        lbl_sub.pack(pady=10)

        self.receive_progress = ctk.CTkProgressBar(self.main_frame, width=400, height=10, 
                                                   progress_color=PRIMARY_PURPLE, fg_color="#222222")
        self.receive_progress.set(0)
        self.receive_progress.pack(pady=20)
        
        self.lbl_status = ctk.CTkLabel(self.main_frame, text="Standing by...", font=("Arial", 14))
        self.lbl_status.pack()

        self.log_box = ctk.CTkTextbox(self.main_frame, height=150, fg_color="#111111", text_color=MUTED_TEXT)
        self.log_box.pack(fill="both", expand=True, padx=30, pady=20)
        
        threading.Thread(target=self.udp_broadcaster, daemon=True).start()
        threading.Thread(target=self.tcp_server, daemon=True).start()

    # --- NETWORK DISCOVERY METHODS ---
    def scan_network(self):
        self.peers = {}
        for w in self.device_list_frame.winfo_children(): w.destroy()
        lbl_scanning = ctk.CTkLabel(self.device_list_frame, text="Scanning local network...", text_color=MUTED_TEXT)
        lbl_scanning.pack(pady=10)
        try:
            msg = f"DISCOVER:{self.username}".encode()
            threading.Thread(target=self.send_broadcast_packet, args=(msg,), daemon=True).start()
        except Exception as e:
            messagebox.showerror("Network Error", str(e))

    def udp_listener(self):
        sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        sock.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
        try:
            sock.bind(('0.0.0.0', UDP_PORT))
        except Exception as e:
            print(f"Failed to bind UDP: {e}")
            return
        
        while True:
            try:
                data, addr = sock.recvfrom(1024)
                msg = data.decode()
                
                if msg.startswith("HERE:"):
                    name = msg.split(":")[1]
                    self.peers[addr[0]] = name
                    self.after(0, self.update_peer_list)
                        
                elif msg.startswith("DISCOVER"):
                    if self.server_running:
                        rs = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
                        rs.sendto(f"HERE:{self.username}".encode(), (addr[0], UDP_PORT))
            except:
                pass

    def udp_broadcaster(self):
        while self.server_running:
            try:
                msg = f"HERE:{self.username}".encode()
                self.send_broadcast_packet(msg)
                time.sleep(2)
            except: break

    def update_peer_list(self):
        if hasattr(self, 'device_list_frame') and self.device_list_frame.winfo_exists():
            for w in self.device_list_frame.winfo_children(): w.destroy()
            if not self.peers:
                ctk.CTkLabel(self.device_list_frame, text="No devices found yet.", text_color=MUTED_TEXT).pack(pady=10)
            for ip, name in self.peers.items():
                btn = ctk.CTkButton(self.device_list_frame, text=f"💻 {name}\n{ip}", height=60,
                                    fg_color="#1A1A1A", hover_color="#2A2A2A", border_width=1, border_color="#333",
                                    command=lambda i=ip, n=name: self.show_sending_progress_ui(i, n))
                btn.pack(pady=5, fill="x", padx=10)

    # --- RECEIVING LOGIC ---
    def tcp_server(self):
        server_socket = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        server_socket.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
        try:
            server_socket.bind(("0.0.0.0", TCP_PORT))
            server_socket.listen(5)
            server_socket.settimeout(1.0) # Allow checking for server_running flag periodically
        except Exception as e:
            print(f"TCP Bind Error: {e}")
            return

        while self.server_running:
            try:
                client, _ = server_socket.accept()
                threading.Thread(target=self.handle_incoming_file, args=(client,), daemon=True).start()
            except socket.timeout:
                continue # Normal timeout, loop checks self.server_running again
            except Exception as e:
                break
        server_socket.close()

    def handle_incoming_file(self, client_socket):
        self.active_receive_sockets.append(client_socket)
        save_path = None
        
        try:
            client_socket.settimeout(10.0)
            header_bytes = b""
            while True:
                if not self.server_running: return # Exit if cancelled during header read
                b = client_socket.recv(1)
                if b == b'\n': break
                header_bytes += b
            
            header = header_bytes.decode()
            filename, filesize = header.split(SEPARATOR)
            filesize = int(filesize)
            
            # Check if UI still exists before updating
            if self.server_running and hasattr(self, 'log_box') and self.log_box.winfo_exists():
                self.after(0, self.log_box.insert, "end", f"↓ Incoming: {filename} ({(filesize/1024/1024):.2f} MB)\n")
                self.after(0, self.receive_progress.set, 0)
            
            downloads_path = Path.home() / "Downloads"
            downloads_path.mkdir(parents=True, exist_ok=True)
            save_path = downloads_path / filename
            
            received_total = 0
            with open(save_path, "wb") as f:
                while received_total < filesize:
                    if not self.server_running: 
                        break # Break loop if user clicks Back
                    
                    bytes_read = client_socket.recv(BUFFER_SIZE)
                    if not bytes_read: break
                    f.write(bytes_read)
                    received_total += len(bytes_read)
                    
                    # Safe UI progress update
                    if self.server_running and hasattr(self, 'receive_progress') and self.receive_progress.winfo_exists():
                        progress = received_total / filesize
                        self.after(0, self.receive_progress.set, progress)
                        
                        # Update status label if it still exists
                        if hasattr(self, 'lbl_status') and self.lbl_status.winfo_exists():
                            self.after(0, lambda txt=f"Receiving {filename}: {int(progress*100)}%": self.lbl_status.configure(text=txt))

            # Transfer finished successfully
            if self.server_running:
                if hasattr(self, 'log_box') and self.log_box.winfo_exists():
                    self.after(0, self.log_box.insert, "end", f"✓ Saved: {filename}\n")
                    if hasattr(self, 'lbl_status') and self.lbl_status.winfo_exists():
                        self.after(0, lambda: self.lbl_status.configure(text="Transfer Complete"))
            else:
                # Cleanup partial file if the transfer was aborted
                if save_path and save_path.exists():
                    try:
                        save_path.unlink()
                    except Exception:
                        pass

        except Exception as e:
            # Only show errors if we are still actually on the receive screen
            if self.server_running and hasattr(self, 'log_box') and self.log_box.winfo_exists():
                self.after(0, self.log_box.insert, "end", f"⚠ Error: {e}\n")
        finally:
            try:
                client_socket.close()
            except Exception: pass
            
            if client_socket in self.active_receive_sockets:
                self.active_receive_sockets.remove(client_socket)

if __name__ == "__main__":
    app = SeamlessApp()
    app.mainloop()