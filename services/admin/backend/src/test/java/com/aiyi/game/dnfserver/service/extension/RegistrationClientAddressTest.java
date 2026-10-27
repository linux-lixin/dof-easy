package com.aiyi.game.dnfserver.service.extension;
import org.junit.Test;
import javax.servlet.http.HttpServletRequest;
import java.lang.reflect.Proxy;
import static org.junit.Assert.*;
public class RegistrationClientAddressTest {
 private HttpServletRequest request(String peer,String real,String forwarded){return (HttpServletRequest)Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{HttpServletRequest.class},(o,m,a)->{if(m.getName().equals("getRemoteAddr"))return peer;if(m.getName().equals("getHeader"))return "X-Real-IP".equals(a[0])?real:forwarded;throw new UnsupportedOperationException(m.getName());});}
 @Test public void externalPeerCannotForgeProxyHeaders(){assertEquals("198.51.100.7",RegistrationClientAddress.resolve(request("198.51.100.7","203.0.113.9","192.0.2.4")));}
 @Test public void localNginxSuppliesAddress(){assertEquals("203.0.113.9",RegistrationClientAddress.resolve(request("127.0.0.1","203.0.113.9","192.0.2.4")));}
 @Test public void canonicalIpv6Address(){assertEquals(RegistrationClientAddress.resolve(request("2001:db8::1",null,null)),RegistrationClientAddress.resolve(request("2001:0db8:0:0:0:0:0:1",null,null)));}
 @Test public void invalidAddressesNeverResolveHostnames(){for(String s:new String[]{"example.com","256.1.1.1","1.2.3","1.2.3.4,5.6.7.8","","127.0.0.1%bad"})try{RegistrationClientAddress.resolve(request("127.0.0.1",s.equals("")?"bad":s,null));fail(s);}catch(ActivityConfigService.Failure e){assertEquals(400,e.status);}}
}
