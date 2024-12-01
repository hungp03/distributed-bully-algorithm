/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.ptithcm.bully.algorithm.util;
import com.ptithcm.bully.algorithm.model.TransactionHistoryModel;

import java.io.FileInputStream;
import java.io.IOException;
import java.sql.*;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/**
 *
 * @author Hung Pham
 */
public class DBConnection {
    private Connection conn;
    public DBConnection() {
        try {
            Properties properties = new Properties();
            try (FileInputStream fis = new FileInputStream("config.properties")) {
                properties.load(fis);
            }

            String url = properties.getProperty("db.url");
            String username = properties.getProperty("db.username");
            String password = properties.getProperty("db.password");
            Class.forName("com.microsoft.sqlserver.jdbc.SQLServerDriver");

            this.conn = DriverManager.getConnection(url, username, password);
            System.out.println("Kết nối thành công!");

        } catch (IOException | SQLException | ClassNotFoundException e) {
            System.out.println("Không thể tạo kết nối đến server.");
            e.printStackTrace();
            this.conn = null;
        }
    }
    public Connection getConnection(){
        return this.conn;
    }
    /*
        Hàm kiểm tra xem tài khoản có tiền hay không
        arg: id của tài khoản
        return: số tiền có trong tài khoản
     */
    public int getAccountMoney(int id) {
        String query = "SELECT AccountMoney FROM account WHERE AccountId = ?";
        try (PreparedStatement stmt = conn.prepareStatement(query)) {
            stmt.setInt(1, id);
            try (ResultSet rel = stmt.executeQuery()) {
                if (rel.next()) {
                    return rel.getInt("AccountMoney");
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return 0;
    }

    /*
        Hàm thực hiện yêu cầu chuyển tiền
        args: id của tài khoản gửi và nhận, số tiền gửi, tin nhắn
        return: void
     */
    public boolean sendMoney(int send, int receive, int money, String msg) {
        String updateQuery = "exec proc_sendmoney ?, ?, ?, ?";
        try (PreparedStatement stmt = this.conn.prepareStatement(updateQuery)) {
            stmt.setInt(1, send);
            stmt.setInt(2, receive);
            stmt.setInt(3, money);
            stmt.setString(4, msg);

            stmt.executeUpdate();
            return true;
        } catch (SQLException e) {
            System.err.println("Lỗi khi chuyển tiền: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }
    public boolean sendMoneyNew(int send, int receive, int money, String msg){
        String updateQuery = "exec sp_new_sendmoney ?, ?, ?, ?";
        try (PreparedStatement stmt = this.conn.prepareStatement(updateQuery)) {
            stmt.setInt(1, send);
            stmt.setInt(2, receive);
            stmt.setInt(3, money);
            stmt.setString(4, msg);

            stmt.executeUpdate();
            return true;
        } catch (SQLException e) {
            System.err.println("Lỗi khi chuyển tiền: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }
    
    public boolean receiveMoney(int send, int receive, int money, String msg){
        String updateQuery = "exec sp_new_receivemoney ?, ?, ?, ?";
        try (PreparedStatement stmt = this.conn.prepareStatement(updateQuery)) {
            stmt.setInt(1, send);
            stmt.setInt(2, receive);
            stmt.setInt(3, money);
            stmt.setString(4, msg);

            stmt.executeUpdate();
            return true;
        } catch (SQLException e) {
            System.err.println("Lỗi khi chuyển tiền: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }
    
    public List<TransactionHistoryModel> getHistoryTransact(int id) {
        List<TransactionHistoryModel> allTransacts = new ArrayList<TransactionHistoryModel>();
        try {
            Statement stmt = this.conn.createStatement();
            String updateQuery = String.format("exec proc_get_history_transaction %d",id);
            System.out.println(updateQuery);
            ResultSet rel = stmt.executeQuery(updateQuery);
            SimpleDateFormat Dateformat = new SimpleDateFormat("yyyyy-mm-dd hh:mm:ss");
            TransactionHistoryModel tmp;
            while(rel.next()){
                int transId = rel.getInt("Id");
                int sendid = rel.getInt("SendId");
                int receiveid = rel.getInt("RecId");
                int money = rel.getInt("Moneys");
                Timestamp date = rel.getTimestamp("TransDate");
                String msg = rel.getString("Msg");
                tmp = new TransactionHistoryModel(transId,sendid,receiveid,money,date,msg);
                allTransacts.add(tmp);
            }
            stmt.close();
            return allTransacts;
        } catch (Exception e) {
            System.out.println(e.getCause());
            System.out.println("Co loi khi tim kiem lich su giao dich");
            return null;

        }
    }
}
