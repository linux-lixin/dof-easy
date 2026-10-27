package com.aiyi.game.dnfserver.service.extension;
import com.aiyi.game.dnfserver.utils.cache.*;
import org.junit.Test;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.Assert.*;
public class RegistrationCaptchaTest {
 @Test public void expiredAndConsumedChallengesCannotReplay(){Key k=Key.as("registration-test",UUID.randomUUID().toString());CacheUtil.put(k,"value",TimeUnit.MILLISECONDS,-1);assertNull(CacheUtil.take(k,String.class));CacheUtil.put(k,"value",TimeUnit.MINUTES,1);assertEquals("value",CacheUtil.take(k,String.class));assertNull(CacheUtil.take(k,String.class));}
 @Test public void concurrentRegistrationsOnlyConsumeOnce()throws Exception{Key k=Key.as("registration-test",UUID.randomUUID().toString());CacheUtil.put(k,"value",TimeUnit.MINUTES,1);ExecutorService pool=Executors.newFixedThreadPool(8);CountDownLatch start=new CountDownLatch(1);try{List<Future<String>> futures=new ArrayList<>();for(int i=0;i<8;i++)futures.add(pool.submit(()->{start.await();return CacheUtil.take(k,String.class);}));start.countDown();int consumed=0;for(Future<String> f:futures)if(f.get(5,TimeUnit.SECONDS)!=null)consumed++;assertEquals(1,consumed);}finally{pool.shutdownNow();}}
}
