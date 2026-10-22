package com.aiyi.game.dnfserver.service;
import com.aliyuncs.CommonRequest;
import com.aliyuncs.CommonResponse;
import com.aliyuncs.DefaultAcsClient;
import com.aliyuncs.IAcsClient;
import com.aliyuncs.exceptions.ClientException;
import com.aliyuncs.exceptions.ServerException;
import com.aliyuncs.http.MethodType;
import com.aliyuncs.profile.DefaultProfile;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;

/**
 * 短信相关
 */
@Component
public class SmsService {

    @Value("${aliyun.access-key-id}")
    private String accessKeyId;

    @Value("${aliyun.access-secret}")
    private String accessSecret;

    @Value("${aliyun.sms.enabled:false}")
    private boolean enabled;

    @Value("${aliyun.sms.sign-name:}")
    private String signName;

    @Value("${aliyun.sms.template-code:}")
    private String templateCode;

    IAcsClient client = null;

    @PostConstruct
    public void init(){
        if (!enabled) return;
        if (accessKeyId == null || accessKeyId.isEmpty() || accessSecret == null || accessSecret.isEmpty()
                || signName == null || signName.isEmpty() || templateCode == null || templateCode.isEmpty()) {
            throw new IllegalStateException("Configure SMS credentials, sign name and template before enabling SMS");
        }
        DefaultProfile profile = DefaultProfile.getProfile("cn-hangzhou", accessKeyId, accessSecret);
        client = new DefaultAcsClient(profile);
    }

    /**
     * 发送验证码
     * @param phone
     *      手机号
     * @param code
     *      验证码
     */
    public void sendCode(String phone, String code) {
        if (!enabled || client == null) {
            throw new IllegalStateException("SMS is disabled for this deployment");
        }
        CommonRequest request = new CommonRequest();
        request.setSysMethod(MethodType.POST);
        request.setSysDomain("dysmsapi.aliyuncs.com");
        request.setSysVersion("2017-05-25");
        request.setSysAction("SendSms");
        request.putQueryParameter("RegionId", "cn-hangzhou");
        request.putQueryParameter("PhoneNumbers", phone);
        request.putQueryParameter("SignName", signName);
        request.putQueryParameter("TemplateCode", templateCode);
        request.putQueryParameter("TemplateParam", "{\"appname\": \"尊敬的\", \"code\": \"" + code + "\"}");
        try {
            CommonResponse response = client.getCommonResponse(request);
            System.out.println(response.getData());
        } catch (ClientException e) {
            e.printStackTrace();
        }
    }
}
