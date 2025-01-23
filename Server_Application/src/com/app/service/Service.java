package com.app.service;

import com.app.apps.MessageType;
import com.app.model.Model_Client;
import com.app.model.Model_File;
import com.app.model.Model_File_Receiver;
import com.app.model.Model_Group;
import com.app.model.Model_Load_Data;
import com.app.model.Model_Login;
import com.app.model.Model_Message;
import com.app.model.Model_Package_Sender;
import com.app.model.Model_Receive_File;
import com.app.model.Model_Receive_Image;
import com.app.model.Model_Receive_Image_Group;
import com.app.model.Model_Receive_Message;
import com.app.model.Model_Receive_Message_Group;
import com.app.model.Model_Register;
import com.app.model.Model_Register_Group;
import com.app.model.Model_Reques_File;
import com.app.model.Model_Send_Message;
import com.app.model.Model_User_Account;
import com.corundumstudio.socketio.AckRequest;
import com.corundumstudio.socketio.Configuration;
import com.corundumstudio.socketio.SocketIOClient;
import com.corundumstudio.socketio.SocketIOServer;
import com.corundumstudio.socketio.listener.ConnectListener;
import com.corundumstudio.socketio.listener.DataListener;
import com.corundumstudio.socketio.listener.DisconnectListener;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.swing.JTextArea;

public class Service {

    private static Service instance;
    private SocketIOServer server;
    private ServiceUser serviceUser;
    private ServiceFile serviceFile;
    private ServiceDataSave serviceDataSave;
    private ServiceGroup serviceGroup;
    private List<Model_Client> listClient;
    private JTextArea textArea;
    private final String PATH_FILE = "server_data/";
    private final int PORT_NUMBER = 9999;

    public static Service getInstance(JTextArea textArea) {
        if (instance == null) {
            instance = new Service(textArea);
        }
        return instance;
    }

    private Service(JTextArea textArea) {
        this.textArea = textArea;
        serviceUser = new ServiceUser();
        serviceFile = new ServiceFile();
        listClient = new ArrayList<>();
        serviceDataSave = new ServiceDataSave();
        serviceGroup = new ServiceGroup();
    }

    public void startServer() {
        Configuration config = new Configuration();
        config.setPort(PORT_NUMBER);
        server = new SocketIOServer(config);
        server.addConnectListener(new ConnectListener() {
            @Override
            public void onConnect(SocketIOClient sioc) {
                textArea.append("One client connected \n");
            }
        });
        
        //Nghe sự kiện "register" từ client và nhận dữ liệu dạng Model_Register.
        server.addEventListener("register", Model_Register.class, new DataListener<Model_Register>() {
            @Override
            public void onData(SocketIOClient sioc, Model_Register t, AckRequest ar) throws Exception {
                // Gọi phương thức register từ serviceUser và nhận kết quả trả về
                Model_Message message = serviceUser.register(t);
                // Gửi kết quả về cho client thông qua Acknowledgment
                ar.sendAckData(message.isAction(), message.getMessage(), message.getData());
                // Nếu đăng ký thành công, thông báo và gửi thông tin người dùng mới đến tất cả các client
                if (message.isAction()) {
                    textArea.append("User has Register : " + t.getUserName() + " Pass :" + t.getPassword() + "\n");
                    // Gửi thông tin người dùng mới đến tất cả các client thông qua sự kiện "list_user"
                    server.getBroadcastOperations().sendEvent("list_user", (Model_User_Account) message.getData());
                    // Thêm client mới vào danh sách
                    addClient(sioc, (Model_User_Account) message.getData());
                }
            }
        });
        
        //Nghe sự kiện "login" từ client và nhận dữ liệu dạng Model_Login.
        server.addEventListener("login", Model_Login.class, new DataListener<Model_Login>() {
            @Override
            public void onData(SocketIOClient sioc, Model_Login t, AckRequest ar) throws Exception {
                // Gọi phương thức login từ serviceUser và nhận thông tin người dùng đăng nhập
                Model_User_Account login = serviceUser.login(t);
                //Kiểm tra đăng nhập
                if (login != null) {
                    //Login thành công
                    // Gửi kết quả về cho client thông qua Acknowledgment và thêm client vào danh sách
                    ar.sendAckData(true, login);
                    addClient(sioc, login);
                    // Thực hiện các hành động liên quan đến việc người dùng kết nối thành công
                    userConnect(login.getUserID());
                } else {
                    //Login thất bại
                    ar.sendAckData(false);
                }
            }

        });

        //Get_username_pass dựa vào ID User
        server.addEventListener("get_username_pass", Integer.class, new DataListener<Integer>() {
            @Override
            public void onData(SocketIOClient sioc, Integer t, AckRequest ar) throws Exception {
                // Gọi phương thức getUserNamePass từ serviceUser và nhận kết quả
                String re = serviceUser.getUserNamePass(t);
                //Gửi trả kết quả cho Client
                ar.sendAckData(re);
            }

        });
        
        //Update_username_pass
        server.addEventListener("update_username_pass", String.class, new DataListener<String>() {
            @Override
            public void onData(SocketIOClient sioc, String message, AckRequest ar) throws Exception {
                // Phân tách thông điệp thành các phần để lấy thông tin cần thiết
                String[] parts = message.split("@");
                // Gọi phương thức updateUser từ serviceUser và nhận kết quả
                boolean re = serviceUser.updateUser(Integer.parseInt(parts[0]), parts[1], parts[2]);
                //Gửi trả kết quả cho Client
                ar.sendAckData(re);
            }
        });
        
        //Nghe sự kiện "user_join_group" từ client và nhận dữ liệu dạng String: tên nhóm
        server.addEventListener("user_join_group", String.class, new DataListener<String>() {
            @Override
            public void onData(SocketIOClient socketIOClient, String groupName, AckRequest ackRequest) throws Exception {
                // Lấy ID User của admin cho nhóm từ serviceGroup
                int adminID = serviceGroup.getAdmin(groupName);
                // Lấy tên người dùng và khởi tạo biến cho kết quả Acknowledgment
                String uName = "";
                boolean isAction = false;
                String messageRe = "Đang chờ phản hồi từ Admin";
                //Gửi trả kết quả cho Client
                ackRequest.sendAckData(isAction, messageRe);
                // Lấy tên người dùng tham gia từ danh sách client
                for (Model_Client d : listClient) {
                    if (d.getClient() == socketIOClient) {
                        uName = d.getUser().getUserName();
                        break;
                    }
                }
                // Tìm admin trong danh sách client và gửi yêu cầu tham gia nhóm
                boolean found = false;
                for (Model_Client c : listClient) {
                    if (c.getUser().getUserID() == adminID) {
                        c.getClient().sendEvent("request_join_group", uName, groupName);
                        found = true;
                        break;
                    }
                }
                // Nếu không tìm thấy admin, thông báo cho người dùng
                if (!found) {
                    String messages = "Admin did not respond!";
                    socketIOClient.sendEvent("send_join_group", messages);
                }
            }
        });

        //Nghe sự kiện "send_join_group" từ client và nhận dữ liệu dạng String: tên nhóm
        server.addEventListener("send_join_group", String.class, new DataListener<String>() {
            @Override
            public void onData(SocketIOClient socketIOClient, String message, AckRequest ackRequest) throws Exception {
                // Phân tách thông điệp thành các phần để lấy thông tin cần thiết
                String[] parts = message.split("@");
                // Kiểm tra xem phản hồi từ admin là "OK" hay không
                if ("OK".equals(parts[0])) {
                    // Tìm client trong danh sách và thực hiện các hành động khi tham gia nhóm thành công
                    for (Model_Client d : listClient) {
                        if (d.getUser().getUserName().equals(parts[1])) {
                            // Tham gia phòng nhóm và gửi thông báo chào mừng
                            d.getClient().joinRoom(parts[2]);
                            String messages = "Welcome to the group";
                            d.getClient().sendEvent("send_join_group", messages);
                            // Lấy ID nhóm và cập nhật thành viên mới vào nhóm
                            int groupID = serviceGroup.getGroupID(parts[2]);
                            serviceGroup.setMemberGroup(groupID, d.getUser().getUserID());
                            break;
                        }
                    }
                //Phản hồi là KHÔNG "OK"
                } else {
                    for (Model_Client d : listClient) {
                        if (d.getUser().getUserName().equals(parts[1])) {
                            String messages = "So sorry";
                            d.getClient().sendEvent("send_join_group", messages);
                            break;
                        }
                    }
                }
                System.out.println(message);
            }
        });

        ////Nghe sự kiện "registerGroup" từ client và nhận dữ liệu dạng Model_Register_Group
        server.addEventListener("registerGroup", Model_Register_Group.class, new DataListener<Model_Register_Group>() {
            @Override
            public void onData(SocketIOClient sioc, Model_Register_Group t, AckRequest ar) throws Exception {
                //Gọi registerGroup từ serviceGroup
                Model_Message message = serviceGroup.registerGroup(t);
                //Thêm nhóm và update trạng thái
                groupAdd(serviceGroup.getGroup(t.getGroupName()));
                ar.sendAckData(message.isAction(), message.getMessage(), message.getData());
            }
        });

        //Nghe sự kiện từ client với tên "list_user" và nhận dữ liệu dạng Integer (ID người dùng).
        server.addEventListener("list_user", Integer.class, new DataListener<Integer>() {
            @Override
            public void onData(SocketIOClient sioc, Integer userID, AckRequest ar) throws Exception {
                try {
                    //Gọi getUser từ serviceUser và nhận ds user
                    List<Model_User_Account> list = serviceUser.getUser(userID);
                    //Gửi ds cho Client
                    sioc.sendEvent("list_user", list.toArray());
                } catch (SQLException e) {
                    System.err.println(e);
                }
            }
        });
        
        //Nghe sự kiện từ client với tên "list_group" và nhận dữ liệu dạng Void.
        server.addEventListener("list_group", Void.class, new DataListener<Void>() {
            @Override
            public void onData(SocketIOClient sioc, Void data, AckRequest ar) throws Exception {
                try {
                    //Gọi getGroup từ serviceGroup và nhận ds group
                    List<Model_Group> list = serviceGroup.getGroups();
                    //Gửi ds cho Client
                    sioc.sendEvent("list_group", list.toArray());
                } catch (SQLException e) {
                    System.err.println(e);
                }
            }
        });
        
        ////Nghe sự kiện từ client với tên "check_group_online" và nhận dữ liệu dạng Void.
        server.addEventListener("check_group_online", Void.class, new DataListener<Void>() {
            @Override
            public void onData(SocketIOClient sioc, Void data, AckRequest ar) throws Exception {
                try {
                    //Gọi getGroup từ serviceGroup và nhận ds group
                    List<Model_Group> list = serviceGroup.getGroups();
                    List<Integer> listID = new ArrayList<>();
                    // Lặp qua danh sách nhóm để kiểm tra xem admin có online không
                    for (Model_Group l : list) {
                        for (Model_Client d : listClient) {
                            if (d.getUser().getUserID() == l.getAdminID()) {
                                // Nếu admin của nhóm online, thêm ID nhóm vào danh sách
                                listID.add(l.getGroupID());
                            }
                        }
                    }
                    // Gửi danh sách ID nhóm có admin online về cho client
                    sioc.sendEvent("check_group_online", listID.toArray());
                } catch (SQLException e) {
                    System.err.println(e);
                }
            }
        });
        
        //Nghe sự kiện từ client với tên "check_group_member" và nhận dữ liệu dạng Integer (ID nhóm).
        server.addEventListener("check_group_member", Integer.class, new DataListener<Integer>() {
            @Override
            public void onData(SocketIOClient socketIOClient, Integer groupID, AckRequest ackRequest) throws Exception {
                boolean found = false;
                boolean isaction = false;
                // Lấy danh sách nhóm từ serviceGroup
                List<Model_Group> list = serviceGroup.getGroups();
                // Lặp qua danh sách client để kiểm tra quyền và tham gia phòng
                for (Model_Client d : listClient) {
                    if (d.getClient() == socketIOClient) {
                        for (Model_Group l : list) {
                            if (l.getAdminID() == d.getUser().getUserID()) {
                                isaction = true;
                                ackRequest.sendAckData(isaction);
                                socketIOClient.joinRoom(serviceGroup.getGroupName(groupID));
                                found = true;
                                break;
                            }
                        }
                    }
                }
                // Nếu client =! admin, kiểm tra xem có là thành viên của nhóm không
                if (!found) {
                    int adminID = serviceGroup.getAdminID(groupID);
                    for (Model_Client d : listClient) {
                        if (d.getUser().getUserID() == adminID) {
                            found = true;
                        }

                    }
                    // Nếu là admin, kiểm tra quyền và tham gia phòng
                    if (found) {
                        for (Model_Client d : listClient) {
                            if (socketIOClient == d.getClient()) {
                                isaction = serviceGroup.checkMemberGroup(groupID, d.getUser().getUserID());
                                ackRequest.sendAckData(isaction);
                                socketIOClient.joinRoom(serviceGroup.getGroupName(groupID));
                                found = true;
                                break;
                            }
                        }
                    }
                }
                //Không thấy quyền và admin
                if (!found) {
                    ackRequest.sendAckData(isaction);
                }

            }
        });
        
        //Nghe sự kiện từ client với tên "get_member_group" và nhận dữ liệu dạng Integer (ID nhóm).
        server.addEventListener("get_member_group", Integer.class, new DataListener<Integer>() {
            @Override
            public void onData(SocketIOClient socketIOClient, Integer groupID, AckRequest ackRequest) throws Exception {
                //Lấy ID từ ds thành viên nhóm từ serviceGroup
                List<Integer> list = new ArrayList<>();
                list = serviceGroup.getMemberGroup(groupID);
                // Tạo danh sách thông tin người dùng tương ứng với các ID thành viên
                List<Model_User_Account> listU = new ArrayList<>();
                for (int l : list) {
                    listU.add(serviceUser.getOneUser(l));
                }
                //Gửi ds về cho Client
                ackRequest.sendAckData(listU.toArray());
            }
        });

        server.addEventListener("join_group", String.class, new DataListener<String>() {
            @Override
            public void onData(SocketIOClient sioc, String t, AckRequest ar) throws Exception {
                try {
                    sioc.joinRoom(t);

                    if (sioc.getAllRooms().isEmpty()) {
                        System.out.println("Không tồn tại room");
                    } else {
                        System.out.println(sioc.getAllRooms().size());
                    }
                    sioc.sendEvent("join_group", t);
                } catch (Exception e) {
                    System.out.println(e + " o join group");
                }
            }
        });

        //leave_group
        server.addEventListener("send_to_user", Model_Send_Message.class, new DataListener<Model_Send_Message>() {
            @Override
            public void onData(SocketIOClient sioc, Model_Send_Message t, AckRequest ar) throws Exception {
                sendToClient(t, ar);
            }
        });

        server.addEventListener("send_to_group", Model_Send_Message.class, new DataListener<Model_Send_Message>() {
            @Override
            public void onData(SocketIOClient sioc, Model_Send_Message t, AckRequest ar) throws Exception {
                String groupName = serviceGroup.getGroupName(t.getToUserID());
                sendToGroup(sioc, t, groupName, ar);
            }
        });

        server.addEventListener("send_avata_to_server", String.class, new DataListener<String>() {
            @Override
            public void onData(SocketIOClient sioc, String t, AckRequest ar) throws Exception {
                System.out.println(t);
                String fileName = "";
                String fileExtension = "";
                int dotIndex = t.lastIndexOf(".");
                if (dotIndex != -1) {
                    fileName = t.substring(0, dotIndex);
                    fileExtension = t.substring(dotIndex, t.length());
                }
                Model_File file = serviceFile.addFileReceiver(fileName, fileExtension);
                serviceFile.initAvata(file);
                ar.sendAckData(file.getFileID());
            }
        });
        server.addEventListener("send_file", Model_Package_Sender.class, new DataListener<Model_Package_Sender>() {
            @Override
            public void onData(SocketIOClient sioc, Model_Package_Sender t, AckRequest ar) throws Exception {
                try {
                    serviceFile.receiveFile(t);
                    if (t.isFinish()) {
                        ar.sendAckData(true);
                        Model_File_Receiver message = serviceFile.setColseFile(t.getFileID());
                        if (message.getMessage().getMessageType() == MessageType.IMAGE.getValue()) {
                            Model_Receive_Image dataImage = new Model_Receive_Image();
                            dataImage.setFileID(t.getFileID());
                            Model_Send_Message messages = serviceFile.closeFileImage(dataImage);

                            sendTempFileImageToClient(messages, dataImage);
                        } else if (message.getMessage().getMessageType() == MessageType.FILE.getValue()) {
                            Model_Receive_File dataFile = new Model_Receive_File();
                            dataFile.setFileID(t.getFileID());
                            Model_Send_Message messages = serviceFile.closeFile(dataFile);

                            sendTempFileToClient(messages, dataFile);
                        }
                    } else {
                        ar.sendAckData(true);
                    }
                } catch (IOException | SQLException e) {
                    ar.sendAckData(false);
                    e.printStackTrace();
                }
            }
        });

        //send_avata
        server.addEventListener("send_avata", Model_Package_Sender.class, new DataListener<Model_Package_Sender>() {
            @Override
            public void onData(SocketIOClient sioc, Model_Package_Sender data, AckRequest ackRequest) throws SQLException {
                try {
                    serviceFile.receiveAvata(data);
                    if (data.isFinish()) {
                        Model_File files = serviceFile.initFile(data.getFileID());
                        String filepath = PATH_FILE + files.getFileID() + "@" + files.getFileName() + files.getFileExtension();
                        File imageFile = new File(filepath);
                        int userID;
                        for (Model_Client d : listClient) {
                            if (d.getClient() == sioc) {
                                userID = d.getUser().getUserID();
                                serviceUser.setUserAvata(userID, imageFile);
                                updateAvata(sioc, userID);
                            }
                        }
                    } else {
                        ackRequest.sendAckData(true);
                    }
                } catch (IOException e) {
                    ackRequest.sendAckData(false);
                    e.printStackTrace();
                }
            }
        });

        //get_avata_user
        server.addEventListener("get_avata_user", Integer.class, new DataListener<Integer>() {
            @Override
            public void onData(SocketIOClient client, Integer t, AckRequest ackRequest) throws SQLException {
                byte[] dataAvata = serviceUser.getUserAvata(t);
                ackRequest.sendAckData(dataAvata);
            }
        });

        server.addEventListener("send_file_group", Model_Package_Sender.class, new DataListener<Model_Package_Sender>() {
            @Override
            public void onData(SocketIOClient sioc, Model_Package_Sender t, AckRequest ar) throws Exception {
                try {
                    serviceFile.receiveFile(t);
                    if (t.isFinish()) {
                        ar.sendAckData(true);
                        Model_File_Receiver message = serviceFile.setColseFile(t.getFileID());
                        if (message.getMessage().getMessageType() == MessageType.IMAGE.getValue()) {
                            Model_Receive_Image dataImage = new Model_Receive_Image();
                            dataImage.setFileID(t.getFileID());
                            Model_Send_Message messages = serviceFile.closeFileImage(dataImage);
                            String groupName = serviceGroup.getGroupName(messages.getToUserID());

                            Model_File file = serviceFile.initFile(t.getFileID());
                            long fileSize = serviceFile.getFileSize(t.getFileID());
                            Model_Receive_Image_Group dataImageGroup = new Model_Receive_Image_Group(dataImage.getFileID(), file.getFileName(), file.getFileExtension(), fileSize, dataImage.getImage(), dataImage.getWidth(), dataImage.getHeight());

                            String filepath = PATH_FILE + file.getFileID() + "@" + file.getFileName() + file.getFileExtension();
                            Path path = Paths.get(filepath);
                            try {
                                byte[] dataIMM = Files.readAllBytes(path);
                                sendTempFileImageToGroup(sioc, messages, groupName, dataImageGroup, dataIMM);
                            } catch (IOException e) {
                                System.err.println("Lỗi khi đọc dữ liệu từ tệp tin: " + e.getMessage());
                            }

                        }
                    } else {
                        ar.sendAckData(true);
                    }
                } catch (IOException | SQLException e) {
                    ar.sendAckData(false);
                    e.printStackTrace();
                }
            }
        });

        server.addEventListener("get_file", Integer.class, new DataListener<Integer>() {
            @Override
            public void onData(SocketIOClient sioc, Integer t, AckRequest ar) throws Exception {
                Model_File file = serviceFile.initFile(t);
                long fileSize = serviceFile.getFileSize(t);
                ar.sendAckData(file.getFileName(), file.getFileExtension(), fileSize);
            }
        });

        server.addEventListener("reques_file", Model_Reques_File.class, new DataListener<Model_Reques_File>() {
            @Override
            public void onData(SocketIOClient sioc, Model_Reques_File t, AckRequest ar) throws Exception {
                byte[] data = serviceFile.getFileDate(t.getCurrentLength(), t.getFileID());
                if (data != null) {
                    ar.sendAckData(data);
                } else {
                    ar.sendAckData();
                }
            }
        });

        server.addEventListener("list_data_user", String.class, new DataListener<String>() {
            @Override
            public void onData(SocketIOClient sioc, String userID, AckRequest ar) throws Exception {
                try {
                    String[] parts = userID.split("@");
                    if (parts.length == 2) {
                        int toID = Integer.parseInt(parts[0]);
                        int uID = Integer.parseInt(parts[1]);
                        System.out.println("u1 = " + toID);
                        System.out.println("u2 = " + uID);
                        List<Model_Load_Data> list = serviceDataSave.getData(toID, uID);
                        sioc.sendEvent("list_data_user", list.toArray());
                    } else {
                        System.out.println("Invalid input format");
                    }
                } catch (SQLException e) {
                    System.err.println(e);
                }
            }

        });
        //user_logout
        server.addEventListener("user_logout", Void.class, new DataListener<Void>() {
            @Override
            public void onData(SocketIOClient sioc, Void data, AckRequest ar) throws Exception {
                int userID = removeClient(sioc);
                if (userID != 0) {
                    //removed
                    userDisconnect(userID);
                    ar.sendAckData(true);
                } else {
                    ar.sendAckData(false);
                }
            }
        });

        server.addDisconnectListener(new DisconnectListener() {
            @Override
            public void onDisconnect(SocketIOClient sioc) {
                int userID = removeClient(sioc);
                if (userID != 0) {
                    //removed
                    userDisconnect(userID);
                }
            }

        });

        server.start();
        textArea.append("Server has start on port : " + PORT_NUMBER + "\n");
    }

    private void userConnect(int userID) {
        server.getBroadcastOperations().sendEvent("user_status", userID, true);
    }

    private void userDisconnect(int userID) {
        server.getBroadcastOperations().sendEvent("user_status", userID, false);
    }

    private void updateAvata(SocketIOClient sioc, int userID) {
        Collection<SocketIOClient> clients = server.getAllClients();
        for (SocketIOClient client : clients) {
            if (!client.equals(sioc)) {
                client.sendEvent("user_update_avata", userID);
            }
        }
    }

    private void groupAdd(Model_Group group) {
        System.out.println("gui thanh cong");
        server.getBroadcastOperations().sendEvent("group_status", group, true);
    }

    private void addClient(SocketIOClient client, Model_User_Account user) {
        listClient.add(new Model_Client(client, user));
    }

    private void sendToClient(Model_Send_Message data, AckRequest ar) throws SQLException {
        if (data.getMessageType() == MessageType.IMAGE.getValue() || data.getMessageType() == MessageType.FILE.getValue()) {
            try {
                Model_File file = serviceFile.addFileReceiver(data.getFileName(), data.getFileExtension());
                serviceFile.initFile(file, data);
                ar.sendAckData(file.getFileID());
                Model_Send_Message datafile = new Model_Send_Message();
                datafile.setFromUserID(data.getFromUserID());
                datafile.setToUserID(data.getToUserID());
                datafile.setMessageType(data.getMessageType());
                datafile.setText(String.valueOf(file.getFileID()));
                serviceDataSave.dataSave(datafile);
            } catch (IOException | SQLException e) {
                e.printStackTrace();
            }

        } else {
            serviceDataSave.dataSave(data);
            for (Model_Client c : listClient) {
                if (c.getUser().getUserID() == data.getToUserID()) {
                    c.getClient().sendEvent("receive_ms", new Model_Receive_Message(data.getMessageType(), data.getFromUserID(), data.getText(), null, null));
                    break;
                }
            }
        }
    }

    private void sendToGroup(SocketIOClient sioc, Model_Send_Message data, String groupName, AckRequest ar) throws SQLException {
        if (data.getMessageType() == MessageType.IMAGE.getValue() || data.getMessageType() == MessageType.FILE.getValue()) {
            try {
                Model_File file = serviceFile.addFileReceiver(data.getFileName(), data.getFileExtension());
                serviceFile.initFile(file, data);
                ar.sendAckData(file.getFileID());
            } catch (IOException | SQLException e) {
                e.printStackTrace();
            }
        } else {
            Collection<SocketIOClient> clientCollection = server.getRoomOperations(groupName).getClients();
            Set<SocketIOClient> clients = new HashSet<>(clientCollection);
            System.out.println("size clients: " + clients.size());
            for (var client : clients) {
                if (!client.equals(sioc)) {
                    client.sendEvent("receive_ms_group", new Model_Receive_Message(data.getMessageType(), data.getFromUserID(), data.getText(), null, null), data.getToUserID());
                }
            }
        }
    }

    private void sendTempFileImageToClient(Model_Send_Message data, Model_Receive_Image dataImage) {
        for (Model_Client c : listClient) {
            if (c.getUser().getUserID() == data.getToUserID()) {
                //System.out.println("ID : " + dataImage.getFileID() + "he: " + dataImage.getHeight() + "wi: " + dataImage.getWidth());
                c.getClient().sendEvent("receive_ms", new Model_Receive_Message(data.getMessageType(), data.getFromUserID(), data.getText(), dataImage, null));
                break;
            }
        }
    }

    private void sendTempFileToClient(Model_Send_Message data, Model_Receive_File dataFile) {
        for (Model_Client c : listClient) {
            if (c.getUser().getUserID() == data.getToUserID()) {
                //System.out.println("ID : " + dataFile.getFileID() + "he: " + dataFile.getFileExtension() + "wi: " + dataFile.getFileSize());
                c.getClient().sendEvent("receive_ms", new Model_Receive_Message(data.getMessageType(), data.getFromUserID(), data.getText(), null, dataFile));
                break;
            }
        }
    }

    private void sendTempFileImageToGroup(SocketIOClient sioc, Model_Send_Message data, String groupName, Model_Receive_Image_Group dataImage, byte[] dataIm) {
        System.out.println("da gui anh ");
        //System.out.println("send_file_group dataIMM 3:" + Arrays.toString(dataIm));
        Collection<SocketIOClient> clientCollection = server.getRoomOperations(groupName).getClients();
        Set<SocketIOClient> clients = new HashSet<>(clientCollection);

        System.out.println("size clients: " + clients.size());
        for (var client : clients) {
            if (!client.equals(sioc)) {
                client.sendEvent("receive_image_group", new Model_Receive_Message_Group(data.getMessageType(), data.getFromUserID(), data.getToUserID(), data.getText(), dataImage, null), dataIm);
            }
        }
    }

    public int removeClient(SocketIOClient client) {
        for (Model_Client d : listClient) {
            if (d.getClient() == client) {
                listClient.remove(d);
                return d.getUser().getUserID();
            }
        }
        return 0;
    }

    public List<Model_Client> getListClient() {
        return listClient;
    }

}
