package com.ptithcm.bully.algorithm.model;

import com.ptithcm.bully.algorithm.util.DBConnection;
import lombok.Data;

import javax.swing.*;
import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.sql.Connection;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.Queue;
import java.util.logging.Level;
import java.util.logging.Logger;

@Data
public class Server {

    private int id;
    private boolean isAdmin = false;
    private int port;
    private ArrayList<Node> listNode = new ArrayList<Node>();
    private JFrame mainFrame;
    private JTextPane textPane;
    private JTextPane tpChatbox;
    private JTextField tfMsg;
    private JButton btnCheckCoor;
    private boolean flagBully = true;
    private String msgText = "";
    public String LogContain = "";
    private Queue<SendMoney> messageQueue;
    private Connection conn;
    public DBConnection connectService;

    public Server(JFrame mainFrame, int port, int id, JTextPane pane, JTextPane chat, JTextField tf, JButton btn) {
        this.mainFrame = mainFrame;
        this.port = port;
        this.id = id;
        this.textPane = pane;
        this.tfMsg = tf;
        this.btnCheckCoor = btn;
        this.tpChatbox = chat;
        this.messageQueue = new LinkedList<>();
        this.connectService = new DBConnection();
        this.conn = connectService.getConnection();
        initListNode();
    }

    /*
        Khởi tạo và chạy một luồng (thread) mới
        để lắng nghe các kết nối đến trên một cổng (port) nhất định
     */
    public void execute() {
        Thread th = new Thread(() -> {
            try (ServerSocket server = new ServerSocket(this.port)) {
                // tạo một vòng lặp liên tục, chấp nhận kết nối khi cần
                while (true) {
                    try {
                        Socket socket = server.accept();
                        System.out.println("Da ket noi voi " + socket);
                        receive(socket);
                        queueResolve();
                    } catch (IOException e) {
                        System.err.println("Lỗi khi nhận kết nối: " + e.getMessage());
                    }
                }
            } catch (IOException ex) {
                System.out.println(ex.getMessage());
                JOptionPane.showMessageDialog(mainFrame, "Không thể tạo server: " + id);
            }
        });
        th.start();
    }

    public void tpSettext(JTextPane pane, String txt) {
        String current = pane.getText();
        pane.setText(txt + "\n" + current);
        LogContain += txt + "\n";
    }

    public void tpSetMessage(JTextPane pane, String msg, int opt) {
        if (opt == 0) {
            msgText += "<h3 style=\"margin-left:150px;background-color:#0984e3;color:#fff;padding:5px;\">" + msg + "</h3>";
        } else {
            msgText += "<h3 style=\"margin-right:150px;background-color:#636e72;color:#fff;padding:5px;\">" + msg + "</h3>";
        }
        pane.setText("");
        pane.setText("""
                     <!DOCTYPE html><html lang="en"><head><meta charset="UTF-8"><meta http-equiv="X-UA-Compatible" content="IE=edge">
                     <meta name="viewport" content="width=device-width, initial-scale=1.0">
                     <style>body {color: #fff;}</style></head>
                     <body>
                     """
                + msgText + "</body>\n" + "</html>");
    }

    /*
         tìm và trả về một Node có vai trò coordinator (điều phối viên)
         Mục đích: tìm một node có ID lớn nhất trong số các node có thể kết nối được
         nếu không có node nào có thể kết nối, trả về null.
         ---------------------------------------
         Hàm ưu tiên trả về node có vai trò admin nếu node đó đã có vai trò điều phối viên.
         ---------------------------------------
         Nếu không có node nào là admin, hàm sẽ tìm node có ID lớn nhất mà có thể kết nối được
         và cấp quyền điều phối viên cho node đó.
         ---------------------------------------
         Sau khi chọn ra node điều phối viên mới,
         hệ thống sẽ cập nhật danh sách các node và trả về node điều phối viên.
     */
    public Node getCoordinator() {
        Node max = null;
        for (Node i : listNode) {
            if (i.isAdmin()) {
                System.out.println("Admin node: " + i.getId());
                return i;
            }
            if (i.getId() > id) {
                try {
                    try (Socket sk = new Socket(i.getHost(), i.getPort())) {
                        max = i;
                    }
                } catch (IOException ignored) {
                }
            }
        }
        if (max != null) {
            max.setAdmin(true);
            listNode.set(listNode.indexOf(max), max);
            return max;
        }
        return null;
    }

    public void initListNode() {
        try {
            Node n = new Node(3000, "192.168.1.10", 0, false);
            Node n1 = new Node(3000, "127.0.0.1", 1, false);
            Node n2 = new Node(3002, "127.0.0.1", 2, false);
            this.listNode.add(n);
            this.listNode.add(n1);
            this.listNode.add(n2);
        } catch (Exception e) {
            System.out.println("error - init list node");
            System.out.println(e.getMessage());
        }
    }

    /*
        Hàm hoạt động như một background worker liên tục kiểm tra và xử lý
        các yêu cầu chuyển tiền trong hệ thống, rồi thông báo kết quả cho các node khác
        (các máy trong mạng). Các yêu cầu trong hàng đợi được xử lý tuần tự,
        nếu không có yêu cầu nào, hàm sẽ chờ đợi cho đến khi có yêu cầu mới
     */
    public void queueResolve() {
        Thread th = new Thread(() -> {
            while (true) {
                // Lấy danh sách các yêu cầu từ hàng đợi
                messageQueue = getMessageQueue();
                if (messageQueue.peek() != null) {
                    try {
                        SendMoney tmp = messageQueue.poll();
                        // Xử lý yêu cầu chuyển tiền
                        connectService.sendMoney(tmp.getSendId(), tmp.getReceiveId(), tmp.getMoney(), tmp.getMsg());
                        System.out.println("#queueResolve : đã chuyển tiền thành công");

                        // Lặp qua tất cả các node để thông báo kết quả
                        for (Node n : getListNode()) {
                            if (n.getId() == tmp.getSendId()) {
                                // Nếu node là người gửi tiền, kết nối để xác nhận
                                try (Socket socket = new Socket(n.getHost(), n.getPort()); DataOutputStream writer = new DataOutputStream(socket.getOutputStream())) {
                                    writer.writeUTF("confirmtransfers");
                                    // Ghi nhận thông báo
//                                     tpSettext(textPane, getCurrentTime() + ":" + getId() + ": " + String.format("Đã chuyển %d đến %d", tmp.MoneyValue, tmp.ReceiveId));
                                }
                            }
                            // Node là người nhận tiền
                            if (n.getId() == tmp.getReceiveId()) {
                                // Nếu node nhận là chính nó
                                if (n.getId() == id) {
                                    tpSettext(textPane, getCurrentTime() + ":" + getId() + ": " + String.format("Vừa nhận được %d đồng từ id: %d", tmp.getMoney(), tmp.getSendId()));
                                    tpSetMessage(tpChatbox, getCurrentTime() + ": Vừa nhận được tiền", 1);
                                    JOptionPane.showMessageDialog(mainFrame, String.format("Bạn vừa nhận được %s từ %s", tmp.getMoney(), tmp.getSendId()));
                                    tfMsg.setText(String.valueOf(connectService.getAccountMoney(id)));
                                    return;
                                }
                                // Nếu node nhận không phải là chính nó, thông báo đến node nhận
                                System.out.println("host: " +n.getHost() + "port: " + n.getPort());
                                try (Socket socket = new Socket(n.getHost(), n.getPort()); DataOutputStream writer = new DataOutputStream(socket.getOutputStream())) {
                                    writer.writeUTF(String.format("receivemoney-%d-%d", tmp.getSendId(), tmp.getMoney()));
                                    tpSettext(textPane, getCurrentTime() + ":" + getId() + ": " + String.format("Đã thông báo đến %d", tmp.getReceiveId()));
                                }
                            }
                        }
                    } catch (Exception e) {
                        System.out.println("#queueResolve : Không thể tạo socket để phản hồi yêu cầu chuyển tiền");
                    }
                } // Nếu không có yêu cầu trong hàng đợi, tạm dừng và đợi
                else {
                    try {
                        queueWait();
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                }
            }
        });
        th.start();
    }

    /*
        Hàm dùng để tạm dừng thread hiện tại cho đến khi có thông báo từ thread khác
        Khi gọi wait(), nó sẽ giải phóng khóa (lock)
        của đối tượng đang đồng bộ, và vào trạng thái chờ (waiting state).
     */
    synchronized void queueWait() throws InterruptedException {
        wait();
    }

    /*
        Đánh thức tất cả các thread đang chờ trên đối tượng này (thông qua wait())
        Khi notifyAll() được gọi, tất cả các thread đang chờ sẽ được đưa vào trạng thái
        có thể tiếp tục thực thi (ready state)
        synchronized đảm bảo rằng chỉ một thread có thể thực thi phương thức queueNotify() tại một thời điểm,
        và nó cũng bảo vệ trạng thái của đối tượng mà các thread đang chờ.
     */
    synchronized void queueNotify() {
        notifyAll();
    }

    public void receive(Socket socket) {
        Thread th = new Thread(() -> {
            try (DataInputStream reader = new DataInputStream(socket.getInputStream()); DataOutputStream writer = new DataOutputStream(socket.getOutputStream())) {
                while (true) {
                    try {
                        String msg = reader.readUTF();
                        System.out.println("Received message: " + msg);
                        String[] list = msg.split("-");
                        System.out.println("Received message: " + list[0]);

                        if (list.length > 5) {
                            for (int i = 5; i < list.length; i++) {
                                list[4] += list[i];
                            }
                        }

                        switch (list[0]) {
                            case "check" ->
                                handleCheckMessage(list, writer);
                            case "election" ->
                                handleElectionMessage(list, writer);
                            case "answer" ->
                                handleAnswerMessage(list);
                            case "coordinator" ->
                                handleCoordinatorMessage(list);
                            case "message" ->
                                handleMessage(list);
                            case "transfers" ->
                                handleTransfersMessage(list, writer);
                            case "confirmtransfers" ->
                                handleConfirmTransfersMessage();
                            case "receivemoney" ->
                                handleReceiveMoneyMessage(list);
                            default ->
                                System.out.println("Received unknown message type");
                        }
                    } catch (EOFException eof) {
                        System.out.println("Client disconnected normally.");
                        break;
                    } catch (IOException ex) {
                        System.out.println("Error while reading message: " + ex.getMessage());
                        break;
                    }
                }
            } catch (IOException ex) {
                System.out.println("Error while handling message: " + ex.getMessage());
            } finally {
                try {
                    socket.close();
                    System.out.println("Socket closed");
                } catch (IOException e) {
                    System.out.println("Error closing socket: " + e.getMessage());
                }
            }
        });

        th.start();
    }

//    public void receive(Socket socket) {
//        Thread th = new Thread(() -> {
//            try (DataInputStream reader = new DataInputStream(socket.getInputStream());
//                 DataOutputStream writer = new DataOutputStream(socket.getOutputStream())) {
//                String msg = reader.readUTF();
//                String[] list = msg.split("-");
//                System.out.println("list: " + msg + " : " + list[0]);
//                if (list.length > 5) {
//                    for (int i = 5; i < list.length; i++) {
//                        list[4] += list[i];
//                    }
//                }
//
//                // Xử lý theo từng loại message
//                switch (list[0]) {
//                    case "check" -> handleCheckMessage(list, writer);
//                    case "election" -> handleElectionMessage(list, writer);
//                    case "answer" -> handleAnswerMessage(list);
//                    case "coordinator" -> handleCoordinatorMessage(list);
//                    case "message" -> handleMessage(list);
//                    case "transfers" -> handleTransfersMessage(list, writer);
//                    case "confirmtransfers" -> handleConfirmTransfersMessage();
//                    case "receivemoney" -> handleReceiveMoneyMessage(list);
//                    default -> System.out.println("Received unknown message type");
//                }
//            } catch (IOException ex) {
//                System.out.println("Error while handling message: " + ex);
//                try {
//                    socket.close();  // Ensure the socket is closed in case of error
//                } catch (IOException e) {
//                    System.out.println("Error closing socket: " + e.getMessage());
//                }
//            }
//        });
//
//        th.start();
//    }
// --- Helper methods for handling different message types ---
    private void handleCheckMessage(String[] list, DataOutputStream writer) throws IOException {
        tpSettext(textPane, getCurrentTime() + ":" + list[1] + ": " + String.join("-", list));
        if (getCoordinator().getId() == id) {
            if (Integer.parseInt(list[1]) < id) {
                tpSetMessage(tpChatbox, list[1] + ": " + list[2], 1);
                writer.writeUTF("response from server");
                forwardMessageToSmallerIdNodes(list, writer);
            } else {
                writer.writeUTF("response from server");
                updateNodeStatus(list[1]);
                bully(1);
            }
        } else {
            bully(0);
        }
    }

    private void handleElectionMessage(String[] list, DataOutputStream writer) throws IOException {
        tpSettext(textPane, getCurrentTime() + ":" + list[1] + ": " + String.join("-", list));
        writer.writeUTF("answer-" + id);
        forwardElectionAnswer(list[1]);
        determineBullyWinner(list[1]);
    }

    private void handleAnswerMessage(String[] list) {
        tpSettext(textPane, getCurrentTime() + ":" + list[1] + ": " + String.join("-", list));
        System.out.println("Received election answer");
    }

    private void handleCoordinatorMessage(String[] list) {
        tpSettext(textPane, getCurrentTime() + ":" + list[1] + ": " + String.join("-", list));
        updateCoordinator(list);
    }

    private void handleMessage(String[] list) {
        tpSetMessage(tpChatbox, list[1] + " : " + list[2], 1);
    }

    private void handleTransfersMessage(String[] list, DataOutputStream writer) throws IOException {
        writer.writeUTF("coordinator da nhan yeu cau chuyen tien");
        System.out.println("server handle: " + this.id);
        SendMoney tmp = new SendMoney(Integer.parseInt(list[1]), Integer.parseInt(list[2]), Integer.parseInt(list[3]), list[4]);
        messageQueue.add(tmp);
        queueNotify();
    }

    private void handleConfirmTransfersMessage() {
        tpSetMessage(tpChatbox, getCurrentTime() + ": Chuyen tien thanh cong!", 1);
        JOptionPane.showMessageDialog(mainFrame, "Chuyen tien thanh cong!");
        tfMsg.setText(String.valueOf(connectService.getAccountMoney(id)));
    }

    private void handleReceiveMoneyMessage(String[] list) {
        connectService.sendMoney(Integer.parseInt(list[1]), this.id, Integer.parseInt(list[2]), "abc");

        tpSetMessage(tpChatbox, getCurrentTime() + ": Just received money", 1);
        tpSettext(textPane, getCurrentTime() + ":" + getId() + ": " + String.format("Received %s from %s", list[2], list[1]));
        JOptionPane.showMessageDialog(mainFrame, String.format("You just received %s from %s", list[2], list[1]));
        tfMsg.setText(String.valueOf(connectService.getAccountMoney(id)));
    }

// --- Helper methods for specific logic ---

    /*
        Chuyển tiếp thông điệp đến các node khác trừ các node đẫ gửi thông điệp
     */
    private void forwardMessageToSmallerIdNodes(String[] list, DataOutputStream writer) {
        for (Node i : listNode) {
            if (i.getId() < id && i.getId() != Integer.parseInt(list[1])) {
                try (Socket msgSocket = new Socket(i.getHost(), i.getPort()); DataOutputStream w = new DataOutputStream(msgSocket.getOutputStream())) {
                    w.writeUTF("message-" + list[1] + "-" + list[2]);
                } catch (Exception ignored) {
                }
            }
        }
    }

    private void updateNodeStatus(String coordinatorId) {
        listNode.forEach(item -> {
            if (item.getId() == Integer.parseInt(coordinatorId)) {
                item.setTimeout(true);
                listNode.set(listNode.indexOf(item), item);
            }
        });
    }

    private void forwardElectionAnswer(String senderId) {
        try (Socket sk = new Socket(getNodeById(senderId).getHost(), getNodeById(senderId).getPort()); DataOutputStream output = new DataOutputStream(sk.getOutputStream())) {
            output.writeUTF("answer-" + id);
            System.out.println("Election answer sent successfully");
        } catch (Exception e) {
            System.out.println("Election answer failed");
        }
    }

    private void determineBullyWinner(String senderId) {
        int maxId = getMaxIdNode(senderId);
        if (maxId == Integer.parseInt(senderId)) {
            bully(2);
        }
    }

    private int getMaxIdNode(String senderId) {
        int max = Integer.parseInt(senderId);
        for (Node item : listNode) {
            if (item.getId() > max && item.getId() < id) {
                max = item.getId();
            }
        }
        return max;
    }

    private Node getNodeById(String id) {
        return listNode.stream()
                .filter(node -> node.getId() == Integer.parseInt(id))
                .findFirst()
                .orElse(null);
    }

    private void updateCoordinator(String[] list) {
        int coordinatorId = Integer.parseInt(list[1]);
        Node currentCoordinator = listNode.stream().filter(node -> node.getId() == coordinatorId).findFirst().orElse(null);
        if (currentCoordinator != null) {
            currentCoordinator.setAdmin(true);
            listNode.forEach(node -> {
                if (node.isAdmin() && node.getId() != coordinatorId) {
                    node.setAdmin(false);
                }
            });
            JOptionPane.showMessageDialog(mainFrame, "New Coordinator with ID: " + list[1]);
        }
    }

    public String getCurrentTime() {
        DateTimeFormatter format = DateTimeFormatter.ofPattern("HH:mm:ss dd/MM/yyyy");
        LocalDateTime date = LocalDateTime.now();
        return (format.format(date));
    }

    public void bully(int opt) {
        //if(flagBully){return;}
        switch (opt) {
            case 0 ->
                JOptionPane.showMessageDialog(mainFrame, "Điều phối viên không phản hổi! \n Bắt đầu thực hiện giải thuật bầu chọn Bully", "Thông báo", JOptionPane.INFORMATION_MESSAGE);
            case 1 ->
                JOptionPane.showMessageDialog(mainFrame, "Bạn vừa nhận request từ một tiến trình có Id lớn hơn! \n Bắt đầu thực hiện giải thuật bầu chọn Bully", "Thông báo", JOptionPane.INFORMATION_MESSAGE);
            case 2 ->
                JOptionPane.showMessageDialog(mainFrame, "Bạn vừa nhận một election \n Bắt đầu thực hiện giải thuật bầu chọn Bully", "Thông báo", JOptionPane.INFORMATION_MESSAGE);
            default -> {
            }
        }

        System.out.println("Bắt đầu thực hiện giải thuật bầu chọn bully");
        int cnt = 0;
        for (Node i : this.listNode) {
            if (i.getId() > this.id) {
                try {
                    try (Socket socket = new Socket(i.getHost(), i.getPort())) {
                        DataInputStream reader = new DataInputStream(socket.getInputStream());
                        DataOutputStream writer = new DataOutputStream(socket.getOutputStream());
                        String msg = "election - " + id;
                        writer.writeUTF(msg);
                        System.out.println("bully send - " + msg);
                        String rev = reader.readUTF();
                        System.out.println("bully rev - " + rev);
                        reader.close();
                        writer.close();
                    }
                    System.out.println("Đã xác nhận có tiến trình id cao hơn");
                    cnt++;
                } catch (IOException ex) {
                    System.out.println("bully - can't create socket connect to " + i.getHost() + i.getPort());
                    //--- có thể không cần field Timeout---
                    listNode.forEach(item -> {
                        if (item.getId() == i.getId()) {
                            item.setTimeout(true);
                            listNode.set(listNode.indexOf(item), item);
                        }
                    });
                    //-------id khong phan hoi--------
                }
            }
        }
        if (cnt > 0) {
            System.out.println("bully confirm-da xac nhan co it nhat 1 tien trinh co Id cao hon minh");
            switch (opt) {
                case 0, 1, 2 ->
                    JOptionPane.showMessageDialog(mainFrame, "Quá trình bầu chọn đã kết thúc!", "Thông báo", JOptionPane.PLAIN_MESSAGE);
                default -> {
                }
            }
            return;
        }
        //--- day la TH khong co tien trinh nao co Id cao hon => gui xac nhan minh chinh la dieu phoi vien
        for (Node n : listNode) {
            if (n.isAdmin()) {
                n.setAdmin(false);
                listNode.set(listNode.indexOf(n), n);
            }
            if (n.getId() == id) {
                n.setAdmin(true);
                listNode.set(listNode.indexOf(n), n);
            }
        }
        JOptionPane.showMessageDialog(mainFrame, "Quá trình bầu chọn đã kết thúc! Bạn chính là điều phối viên mới.", "Thông báo", JOptionPane.PLAIN_MESSAGE);
        //--- gui xac nhan dieu phoi vien moi
        Coordinator();
        //flagBully = true;
    }

    public void Coordinator() {
        for (Node n : listNode) {
            if (this.id != n.getId()) {
                Thread th = new Thread() {
                    @Override
                    public void run() {
                        try {
                            //DataInputStream reader = new DataInputStream(socket.getInputStream());
                            try ( //--- chỉ gửi thông điệp, không cần quan tâm phản hồi ---
                                    Socket socket = new Socket(n.getHost(), n.getPort()); //DataInputStream reader = new DataInputStream(socket.getInputStream());
                                     DataOutputStream writer = new DataOutputStream(socket.getOutputStream())) {
                                writer.writeUTF("coordinator-" + id);
                                System.out.println("da send coordinator to " + n.getId());
                            }
                        } catch (IOException e) {
                            System.out.println("node " + n.getId() + " was broke");
                        }
                    }
                };
                th.start();
            }
        }
    }

    public void sendMoneyRequestToCoordinator(String msg) {
        Node n = getCoordinator();
        if (n == null) {
            System.out.println("checkCoordinator - không tìm được Coordinator");
            return;
        }
        try {
            try (Socket socket = new Socket(n.getHost(), n.getPort())) {
                DataOutputStream writer;
                try (DataInputStream reader = new DataInputStream(socket.getInputStream())) {
                    writer = new DataOutputStream(socket.getOutputStream());
                    writer.writeUTF(msg);
                    System.out.println("sendmoneyrequest send - " + msg);
                    String rev = reader.readUTF();
                    System.out.println("sendmoneyrequest receive - " + rev);
                    tpSettext(textPane, getCurrentTime() + ":" + n.getId() + ": " + rev);
                }
                writer.close();
            }
        } catch (IOException ex) {
            System.out.println("sendmoneyrequest create socket to server");
            bully(0);
        }
    }

    public void WriteLog(String filename) throws IOException {
        try {
            PrintWriter pw = new PrintWriter(new FileWriter("D:\\Netbean\\bully-algorithm\\src\\main\\java\\com\\ptithcm\\bully\\algorithm\\log\\" + filename, true));
            pw.println(LogContain);
            pw.flush();
            pw.close();
        } catch (FileNotFoundException ex) {
            Logger.getLogger(Server.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
}
