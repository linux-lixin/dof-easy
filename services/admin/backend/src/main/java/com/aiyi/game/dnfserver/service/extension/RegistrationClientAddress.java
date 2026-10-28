package com.aiyi.game.dnfserver.service.extension;

import javax.servlet.http.HttpServletRequest;
import java.net.InetAddress;

/** Only the local nginx peer may supply its overwritten X-Real-IP header. Never trusts body/XFF. */
public final class RegistrationClientAddress {
    private RegistrationClientAddress(){}
    public static String resolve(HttpServletRequest request){
        String peer=request.getRemoteAddr(),value=peer;
        if("127.0.0.1".equals(peer)||"::1".equals(peer)||"0:0:0:0:0:0:0:1".equals(peer)){
            String real=request.getHeader("X-Real-IP");if(real!=null&&!real.isEmpty())value=real;
        }
        if(value==null||value.length()>64||!value.matches("[0-9a-fA-F:.]+")||value.indexOf(':')<0&&!value.matches("[0-9]{1,3}(\\.[0-9]{1,3}){3}"))throw new ActivityConfigService.Failure(400,"客户端注册地址无效");
        if(value.indexOf(':')<0)for(String s:value.split("\\."))if(Integer.parseInt(s)>255)throw new ActivityConfigService.Failure(400,"客户端注册地址无效");
        try{return InetAddress.getByName(value).getHostAddress().toLowerCase(java.util.Locale.ROOT);}catch(Exception e){throw new ActivityConfigService.Failure(400,"客户端注册地址无效");}
    }
}
