package com.aiyi.game.dnfserver.service.dof;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import javax.annotation.Resource;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;

/** Domain-separated private evidence; passwords and codes never enter ordinary journals. */
@Service
public class RegistrationProof {
    @Resource private ManagedOperationJournal journal;
    @Value("${DOF_REGISTRATION_STATE_KEY_FILE:./data/private.key}") private String keyFile;
    byte[] key(){try{Path p=Paths.get(keyFile);if(Files.isSymbolicLink(p)||!Files.isRegularFile(p,LinkOption.NOFOLLOW_LINKS)||Files.size(p)<32||Files.size(p)>65536)throw new Exception();MessageDigest d=MessageDigest.getInstance("SHA-256");d.update("dof-registration-state-v1\0".getBytes(StandardCharsets.UTF_8));return d.digest(Files.readAllBytes(p));}catch(Exception e){throw new DofFailure(503,"注册核对私密材料不可用；未生成替代材料或写入玩家数据");}}
    byte[] bytes(byte[] key,String domain,Object value){try{Mac mac=Mac.getInstance("HmacSHA256");mac.init(new SecretKeySpec(key,"HmacSHA256"));return mac.doFinal((domain+"\0"+journal.encode(value)).getBytes(StandardCharsets.UTF_8));}catch(java.security.GeneralSecurityException e){throw new DofFailure(503,"注册核对材料不可用");}}
    String tag(byte[] key,String domain,Object value){StringBuilder out=new StringBuilder();for(byte b:bytes(key,domain,value))out.append(String.format("%02x",b&255));return out.toString();}
    String keyId(byte[] key){return tag(key,"key-id",journal.instance());}
    public String ipTag(String ip){return tag(key(),"registration-ip",Arrays.asList(journal.instance(),ip));}
    public String currentKeyId(){return keyId(key());}
    String code(byte[] key,String id,long actor,int days,long expires){return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes(key,"activation-code",Arrays.asList(journal.instance(),id,actor,days,expires)));}
}
