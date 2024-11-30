/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.ptithcm.bully.algorithm.model;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.Date;

/**
 *
 * @author Hung Pham
 */
@Data
@AllArgsConstructor
public class TransactionHistoryModel {
    private int id;
    private int sendId;
    private int receiveId;
    private int money;
    private Date date;
    private String msg;
}
