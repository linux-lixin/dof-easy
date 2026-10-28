package com.aiyi.game.dnfserver.controller;

import com.aiyi.game.dnfserver.conf.NoLogin;
import com.aiyi.game.dnfserver.dao.NoticeDao;
import com.aiyi.game.dnfserver.entity.Notice;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Collections;
import java.util.Map;

@RestController
@RequestMapping("api/v1/notice")
public class NoticeController {

    @Resource
    private NoticeDao noticeDao;

    @Resource
    private com.aiyi.game.dnfserver.service.extension.ExtensionQueryService security;

    @GetMapping
    @NoLogin
    public Notice getNotice(){
        Notice notice = noticeDao.get(1);
        if (notice == null) { notice = new Notice(); notice.setId(1); notice.setContent(""); return notice; }
        notice.setContent(new String(Base64.getDecoder().decode(notice.getContent()), StandardCharsets.UTF_8));
        return notice;
    }

    @PutMapping
    public ResponseEntity<Map<String,String>> setNotice(@RequestBody(required=false) Notice ignored){
        security.requireAdmin();
        // The old entry bypassed the durable management protocol. The bound
        // 86-level instance has no verified login_notice table; do not create
        // one or pretend that a versioned replacement adapter already exists.
        return ResponseEntity.status(HttpStatus.GONE).body(Collections.singletonMap(
                "message", "旧无编号公告写入口已停用；公告表及固定预览、回执和恢复适配尚待核实，当前不提供公告写入"));
    }

}
