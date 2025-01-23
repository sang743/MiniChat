package com.app.service;

import com.app.apps.MessageType;
import com.app.connection.DatabaseConnection;
import com.app.model.Model_Avata_Receiver;
import com.app.model.Model_File;
import com.app.model.Model_File_Receiver;
import com.app.model.Model_File_Sender;
import com.app.model.Model_Package_Sender;
import com.app.model.Model_Receive_File;
import com.app.model.Model_Receive_Image;
import com.app.model.Model_Send_Message;
import com.app.swing.blurhash.BlurHash;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.sql.PreparedStatement;
import java.nio.file.Paths;
import java.nio.file.Files;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.ResultSet;
import java.util.HashMap;
import java.util.Map;
import javax.imageio.ImageIO;

public class ServiceFile {

    public ServiceFile() {
        this.con = DatabaseConnection.getInstance().getConnection();
        this.fileReceivers = new HashMap<>();
        this.fileSenders = new HashMap<>();
        this.avataReceivers = new HashMap<>();
    }

    public Model_File addFileReceiver(String fileName, String fileExtension) throws SQLException {
        Model_File data;
        PreparedStatement p = con.prepareStatement(INSERT, PreparedStatement.RETURN_GENERATED_KEYS);
        p.setString(1, fileName);
        p.setString(2, fileExtension);
        p.execute();
        ResultSet r = p.getGeneratedKeys();
        r.first();
        int fileID = r.getInt(1);
        data = new Model_File(fileID, fileName, fileExtension);
        r.close();
        p.close();
        return data;
    }

    public void updateBlurHashDone(int fileID, String blurhash) throws SQLException {
        PreparedStatement p = con.prepareStatement(UPDATE_BLUR_HASH_DONE);
        p.setString(1, blurhash);
        p.setInt(2, fileID);
        p.execute();
        p.close();
    }
    
     public void updateFileContent(int fileID, byte[] fileContent) throws SQLException {
        PreparedStatement p = con.prepareStatement(UPDATE_BLUR_FILE_CONTENT);
        p.setBytes(1, fileContent);
        p.setInt(2, fileID);
        p.execute();
        p.close();
    }
     
    public void updateDone(int fileID) throws SQLException {
        PreparedStatement p = con.prepareStatement(UPDATE_DONE);
        p.setInt(1, fileID);
        p.execute();
        p.close();
    }

    public void initFile(Model_File file, Model_Send_Message message) throws IOException {
        fileReceivers.put(file.getFileID(), new Model_File_Receiver(message, toFileObject(file)));
    }
    
    public void initAvata(Model_File file) throws IOException{
        avataReceivers.put(file.getFileID(), new Model_Avata_Receiver(toFileObject(file)));
    }

    public Model_File getFile(int fileID) throws SQLException{
        PreparedStatement p = con.prepareStatement(GET_FILE_EXTENSION);
        p.setInt(1, fileID);
        ResultSet r =p.executeQuery();
        r.next(); ////// sai -> .next()
        String fileName = r.getString(1);
        String fileExtension = r.getString(2);
        Model_File data = new Model_File(fileID, fileName, fileExtension);
        r.close();
        p.close();
        return data;
    }
    public synchronized Model_File initFile(int fileID) throws IOException, SQLException{
        Model_File file;
        if(!fileSenders.containsKey(fileID)){
            file = getFile(fileID);
            fileSenders.put(fileID, new Model_File_Sender(file, new File(PATH_FILE + fileID + "@" + file.getFileName() + file.getFileExtension())));
        } else {
            file = fileSenders.get(fileID).getData();
        }
        return file;
    }
    
    public byte[] getFileDate(long currentLength, int fileID) throws IOException, SQLException{
        initFile(fileID);
        return fileSenders.get(fileID).read(currentLength);
    }
    
    public byte[] getFileDate(int fileID) throws IOException, SQLException{
        initFile(fileID);
        return fileSenders.get(fileID).read();
    }
    
    public long getFileSize(int fileID){
        return fileSenders.get(fileID).getFileSize();
    }
    
    public void receiveFile(Model_Package_Sender dataPackage) throws IOException {
        if (!dataPackage.isFinish()) {
            fileReceivers.get(dataPackage.getFileID()).writeFile(dataPackage.getData());
        } else {
            fileReceivers.get(dataPackage.getFileID()).close();
        }
    }
    
    public void receiveAvata(Model_Package_Sender dataPackage) throws IOException {
        if (!dataPackage.isFinish()) {
            avataReceivers.get(dataPackage.getFileID()).writeFile(dataPackage.getData());
        } else {
            avataReceivers.get(dataPackage.getFileID()).close();
        }
    }
    
    public Model_File_Receiver setColseFile(int fileID){
        Model_File_Receiver file = fileReceivers.get(fileID);
        return file;
    }
    public Model_Send_Message closeFileImage(Model_Receive_Image dataImage) throws IOException, SQLException {
        Model_File_Receiver file = fileReceivers.get(dataImage.getFileID());
        if (file.getMessage().getMessageType() == MessageType.IMAGE.getValue()) {
            //Image file
            //So creat blurhash image string
            file.getMessage().setText("");
            String blurhash = convertFileToBlurHash(file.getFile(), dataImage);
            updateBlurHashDone(dataImage.getFileID(),blurhash);
        } else {
            updateDone(dataImage.getFileID());
        }
        fileReceivers.remove(dataImage.getFileID());
        //get message to send to target client when file receive finish
        return file.getMessage();
    }
    public Model_Send_Message closeFile(Model_Receive_File dataFile) throws IOException, SQLException {
        Model_File_Receiver file = fileReceivers.get(dataFile.getFileID());
        Model_File files;
        if (file.getMessage().getMessageType() == MessageType.FILE.getValue()) {
            file.getMessage().setText("");
            files = getFile(dataFile.getFileID());
            String filePath = PATH_FILE + files.getFileID() + "@" + files.getFileName() + files.getFileExtension();
            byte[] fileContent = Files.readAllBytes(Paths.get(filePath));
            dataFile.setFileExtension(files.getFileExtension());
            dataFile.getFileSize();
            dataFile.setData(fileContent);
            updateFileContent(dataFile.getFileID(), fileContent);
        } else {
            updateDone(dataFile.getFileID());
        }
        fileReceivers.remove(dataFile.getFileID());
        //get message to send to target client when file receive finish
        return file.getMessage();
    }
   
    private String convertFileToBlurHash(File file, Model_Receive_Image dataImage) throws IOException {
        BufferedImage img = ImageIO.read(file);
        Dimension size = getAutoSize(new Dimension(img.getWidth(), img.getHeight()), new Dimension(200, 200));
        // Convert image to small size
        BufferedImage newImage = new BufferedImage(size.width, size.height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = newImage.createGraphics();
        g2.drawImage(img, 0, 0, size.width, size.height, null);
        String blurhash = BlurHash.encode(newImage);
        dataImage.setWidth(size.width);
        dataImage.setHeight(size.height);
        dataImage.setImage(blurhash);
        return blurhash;
    }

    private Dimension getAutoSize(Dimension fromSize, Dimension toSize) {
        int w = toSize.width;
        int h = toSize.height;
        int iw = fromSize.width;
        int ih = fromSize.height;
        double xScale = (double) w / iw;
        double yScale = (double) h / ih;
        double scale = Math.min(xScale, yScale);
        int width = (int) (scale * iw);
        int height = (int) (scale * ih);
        return new Dimension(width, height);
    }

    private File toFileObject(Model_File file) {
        return new File(PATH_FILE + file.getFileID() + "@" + file.getFileName() + file.getFileExtension());
    }
    //SQL
    private final String PATH_FILE = "server_data/";
    private final String INSERT = "insert into files (`FileName`, `FileExtension`) values (?,?)";
    private final String UPDATE_BLUR_HASH_DONE = "update files set BlurHash=?, `Status`='1' where FileID=? limit 1";
    private final String UPDATE_BLUR_FILE_CONTENT = "update files set File_Content=?, `Status`='1' where FileID=? limit 1";
    private final String UPDATE_DONE = "update files set `Status`='1' where FileID=? limit 1";
    private final String GET_FILE_EXTENSION = "select FileName,FileExtension from files where FileID=? limit 1";
    //Instance
    private final Connection con;
    private final Map<Integer, Model_File_Receiver> fileReceivers;
    private final Map<Integer, Model_File_Sender> fileSenders;
    private final Map<Integer, Model_Avata_Receiver> avataReceivers;
}
