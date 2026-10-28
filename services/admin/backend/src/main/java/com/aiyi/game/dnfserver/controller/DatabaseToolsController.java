package com.aiyi.game.dnfserver.controller;

import com.aiyi.game.dnfserver.entity.DataBaseExcute;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.*;

/**
 * Copyright (c) 2021 HEBEI CLOUD IOT FACTORY BIGDATA CO.,LTD.
 * Legal liability shall be investigated for unauthorized use
 *
 * @Author: Guo Shengkai
 * @Date: Create in 2021/04/29 14:37
 */
@RestController
@RequestMapping("api/v1/database")
public class DatabaseToolsController {

    /** Retain the legacy URL only to return an explicit retirement response. */
    @PostMapping
    public ResponseEntity<Map<String, String>> excute(@RequestBody(required = false) DataBaseExcute ignored){
        // Never decode, log or execute browser-supplied SQL, including SELECT.
        return ResponseEntity.status(HttpStatus.GONE).body(Collections.singletonMap(
                "message", "任意 SQL 入口已停用；请使用具备权限、预览、操作编号和结果核对的固定管理动作"));
    }


}
