/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.ptithcm.bully.algorithm.model;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * @author Hung Pham
 */
@Data
public class Node {
    private int id;
    private int port;
    private String host;
    private boolean isAdmin;
    private boolean timeout = false;

    public Node(int port, String host, int id, boolean isAdmin) {
        this.port = port;
        this.host = host;
        this.id = id;
        this.isAdmin = isAdmin;
    }
}
