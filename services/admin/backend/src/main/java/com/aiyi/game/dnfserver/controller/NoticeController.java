package com.aiyi.game.dnfserver.controller;

import com.aiyi.game.dnfserver.conf.NoLogin;
import com.aiyi.game.dnfserver.dao.NoticeDao;
import com.aiyi.game.dnfserver.entity.Notice;
import com.aiyi.game.dnfserver.utils.ChinaseUtil;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

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
    public void setNotice(@RequestBody Notice notice){
        security.requireAdmin();
        if (notice == null || notice.getId() != 1 || notice.getContent() == null || notice.getContent().length() > 16000)
            throw new com.aiyi.game.dnfserver.service.extension.ActivityConfigService.Failure(400, "仅允许修改主公告，内容最多 16000 字符");
        notice.setContent(Base64.getEncoder().encodeToString(notice.getContent().getBytes(StandardCharsets.UTF_8)));
        noticeDao.update(notice);
    }

}
