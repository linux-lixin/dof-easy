package com.aiyi.game.dnfserver.service.extension;
import org.junit.Test;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import static org.junit.Assert.*;
public class PasswordChallengesTest {
 @Test public void expiresAndConsumesWrongAttempts(){
  AtomicLong time=new AtomicLong();PasswordChallenges c=new PasswordChallenges(time::get);
  c.issue("a","VALUE");assertFalse(c.consume("a","wrong"));assertFalse(c.consume("a","VALUE"));
  c.issue("a","VALUE");time.set(300000);assertFalse(c.consume("a","VALUE"));
 }
 @Test public void concurrentSubmissionOnlySucceedsOnce()throws Exception {
  PasswordChallenges c=new PasswordChallenges();c.issue("a","VALUE");
  ExecutorService pool=Executors.newFixedThreadPool(2);CountDownLatch start=new CountDownLatch(1);
  try{Callable<Boolean> task=()->{start.await();return c.consume("a","value");};
   Future<Boolean> a=pool.submit(task),b=pool.submit(task);start.countDown();assertNotEquals(a.get(),b.get());
  }finally{pool.shutdownNow();}
 }
}
