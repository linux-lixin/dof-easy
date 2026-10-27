package com.aiyi.game.dnfserver.controller;

import com.aiyi.core.beans.ResultPage;
import com.aiyi.game.dnfserver.entity.Postal;
import com.aiyi.game.dnfserver.service.PostalService;
import com.aiyi.game.dnfserver.utils.ChinaseUtil;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.Date;

/**
 * Copyright (c) 2021 HEBEI CLOUD IOT FACTORY BIGDATA CO.,LTD.
 * Legal liability shall be investigated for unauthorized use
 *
 * @Author: Guo Shengkai
 * @Date: Create in 2021/04/13 16:42
 */
@RestController
@RequestMapping("api/v1/postal")
public class PostalController {

    @ExceptionHandler(com.aiyi.game.dnfserver.service.dof.DofFailure.class)
    public org.springframework.http.ResponseEntity<java.util.Map<String,String>> managementFailure(com.aiyi.game.dnfserver.service.dof.DofFailure e){
        return org.springframework.http.ResponseEntity.status(e.status).body(java.util.Collections.singletonMap("message",e.getMessage()));
    }

    @ExceptionHandler(org.springframework.dao.DataAccessException.class)
    public org.springframework.http.ResponseEntity<java.util.Map<String,String>> databaseFailure(org.springframework.dao.DataAccessException e,javax.servlet.http.HttpServletRequest request){
        if(!"GET".equals(request.getMethod()))
            return org.springframework.http.ResponseEntity.status(503).body(java.util.Collections.singletonMap("message",
                "邮件写入结果尚未确认，请核对邮件记录，不要直接重发；持久化操作协议尚未接入此旧发送入口"));
        boolean structure = e instanceof org.springframework.jdbc.BadSqlGrammarException;
        return org.springframework.http.ResponseEntity.status(structure ? 409 : 503).body(java.util.Collections.singletonMap("message",
            structure ? "邮件查询所需的账号、角色或邮件表结构不兼容，请核对绑定实例；没有修改数据" : "邮件数据库查询失败，请检查连接及读取权限；未返回空结果代替错误"));
    }

    @Resource
    private PostalService postalService;

    @GetMapping
    public ResultPage<Postal> list(String account, String start,
                                   String end, Integer page,
                                   Integer pageSize){
        if (null == page) page = 1;
        if (null == pageSize) pageSize = 10;
        ResultPage<Postal> list = postalService.list(account, queryTime(start,false), queryTime(end,true),
                page, pageSize);
        for (Postal postal: list.getList()){
            ChinaseUtil.toSimple(postal);
        }
        return list;
    }

    private Date queryTime(String value,boolean endOfDay){
        if(value == null || value.isEmpty()) return null;
        if(!value.matches("\\d{4}-\\d{2}-\\d{2}( \\d{2}:\\d{2}:\\d{2})?"))
            throw new com.aiyi.game.dnfserver.service.dof.DofFailure(400,"邮件时间须为 yyyy-MM-dd 或 yyyy-MM-dd HH:mm:ss");
        boolean dayOnly = value.length() == 10;
        String text = dayOnly ? value + (endOfDay ? " 23:59:59" : " 00:00:00") : value;
        java.text.SimpleDateFormat format = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        format.setLenient(false);
        java.text.ParsePosition position = new java.text.ParsePosition(0);
        Date result = format.parse(text,position);
        if(result == null || position.getIndex() != text.length())
            throw new com.aiyi.game.dnfserver.service.dof.DofFailure(400,"邮件筛选时间无效");
        return dayOnly && endOfDay ? new Date(result.getTime()+999) : result;
    }

    @PostMapping
    public void sendMail(@RequestBody Postal postal){
        postalService.sendMail(postal);
    }

}
