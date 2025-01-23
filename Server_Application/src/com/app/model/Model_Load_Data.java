package com.app.model;

import java.sql.Timestamp;
import java.util.Date;

public class Model_Load_Data {

    public Model_Receive_File getDataFile() {
        return dataFile;
    }

    public void setDataFile(Model_Receive_File dataFile) {
        this.dataFile = dataFile;
    }
    
    public Model_Receive_Image getDataImage() {
        return dataImage;
    }

    public void setDataImage(Model_Receive_Image dataImage) {
        this.dataImage = dataImage;
    }
    public int getMessageType() {
        return messageType;
    }

    public void setMessageType(int messageType) {
        this.messageType = messageType;
    }

    public int getFromUserID() {
        return fromUserID;
    }

    public void setFromUserID(int fromUserID) {
        this.fromUserID = fromUserID;
    }

    public int getToUserID() {
        return toUserID;
    }

    public void setToUserID(int toUserID) {
        this.toUserID = toUserID;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }
    
    public Date getDateTime() {
        return dateTime;
    }

    public void setDateTime(Timestamp dateTime) {
        this.dateTime = dateTime;
    }
    
    public Model_Load_Data(int messageType, int fromUserID, int toUserID, String text, Timestamp  dateTime, Model_Receive_Image dataImage, Model_Receive_File dataFile){
        this.messageType = messageType;
        this.fromUserID = fromUserID;
        this.toUserID = toUserID;
        this.text = text;
        this.dateTime = dateTime;
        this.dataImage = dataImage;
        this.dataFile = dataFile;
    }
    
    public Model_Load_Data(){
    }
          
    private int messageType;
    private int fromUserID;
    private int toUserID;
    private String text;
    private Timestamp  dateTime;
    private Model_Receive_Image dataImage;
    private Model_Receive_File dataFile;
 

}

