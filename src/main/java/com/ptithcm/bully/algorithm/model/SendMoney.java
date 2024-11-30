/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.ptithcm.bully.algorithm.model;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 *
 * @author Hung Pham
 */
@Data
@AllArgsConstructor
public class SendMoney {
    private int sendId;
    private int receiveId;
    private int money;
    private String msg;
}
