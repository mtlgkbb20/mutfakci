package com.kitchenai.auth;
import com.kitchenai.common.ApiException;
import com.kitchenai.user.User;
import com.kitchenai.user.UserRepository;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.*;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EmailVerificationService {
 private static final SecureRandom RANDOM=new SecureRandom();
 private static final int MAX_ATTEMPTS=5;
 private final JdbcTemplate jdbc; private final UserRepository users;
 private final JavaMailSender mail; private final String from; private final byte[] secret;
 public EmailVerificationService(JdbcTemplate jdbc,UserRepository users,JavaMailSender mail,
   @Value("${app.email.from}") String from,
   @Value("${security.otp.hash-secret:${jwt.secret}}") String secret) {
  this.jdbc=jdbc; this.users=users; this.mail=mail; this.from=from;
  this.secret=secret.getBytes(StandardCharsets.UTF_8);
 }
 @Transactional
 public void sendCode(User user,String ip) {
  rate("email:"+user.getEmail().toLowerCase(),3);
  rate("ip:"+safeIp(ip),10);
  var rows=jdbc.query("SELECT resend_after,consumed_at FROM email_verification WHERE user_id=? FOR UPDATE",
   (rs,n)->new Object[]{rs.getTimestamp(1).toInstant(),rs.getTimestamp(2)==null?null:rs.getTimestamp(2).toInstant()},user.getId());
  Instant now=Instant.now();
  if(!rows.isEmpty() && ((Instant)rows.get(0)[0]).isAfter(now) && rows.get(0)[1]==null)
   throw new ApiException(HttpStatus.TOO_MANY_REQUESTS,"Please wait before requesting another code.");
  String code=String.format("%06d",RANDOM.nextInt(1_000_000));
  jdbc.update("""
   INSERT INTO email_verification(user_id,code_hash,expires_at,attempts,resend_after,consumed_at,created_at)
   VALUES(?,?,?,0,?,NULL,?) ON CONFLICT(user_id) DO UPDATE SET
   code_hash=EXCLUDED.code_hash,expires_at=EXCLUDED.expires_at,attempts=0,
   resend_after=EXCLUDED.resend_after,consumed_at=NULL,created_at=EXCLUDED.created_at
   """,user.getId(),hash(code),java.sql.Timestamp.from(now.plus(Duration.ofMinutes(10))),
   java.sql.Timestamp.from(now.plusSeconds(60)),java.sql.Timestamp.from(now));
  SimpleMailMessage message=new SimpleMailMessage();
  message.setFrom(from); message.setTo(user.getEmail());
  message.setSubject("Kitchen AI e-posta doğrulama kodun");
  message.setText("Kitchen AI hesabını doğrulamak için kodun: "+code+
   "\n\nBu kod 10 dakika içinde sona erer. Bu isteği sen yapmadıysan e-postayı yok say.");
  mail.send(message);
 }
 @Transactional(noRollbackFor = ApiException.class)
 public User verify(String email,String code,String ip) {
  rate("verify-ip:"+safeIp(ip),20);
  User user=users.findByEmailIgnoreCase(email==null?"":email.trim()).orElseThrow(this::invalid);
  if(user.isEmailVerified()) return user;
  var rows=jdbc.query("""
   SELECT code_hash,expires_at,attempts,consumed_at FROM email_verification
   WHERE user_id=? FOR UPDATE
   """,(rs,n)->new Verification(rs.getString(1),rs.getTimestamp(2).toInstant(),
    rs.getInt(3),rs.getTimestamp(4)==null?null:rs.getTimestamp(4).toInstant()),user.getId());
  if(rows.isEmpty()) throw invalid();
  Verification v=rows.get(0);
  if(v.consumed!=null || v.expires.isBefore(Instant.now()) || v.attempts>=MAX_ATTEMPTS) throw invalid();
  String supplied=code==null?"":code.trim();
  if(!MessageDigest.isEqual(v.hash.getBytes(StandardCharsets.US_ASCII),
    hash(supplied).getBytes(StandardCharsets.US_ASCII))) {
   int attempts=v.attempts+1;
   jdbc.update("UPDATE email_verification SET attempts=?,consumed_at=CASE WHEN ? >= ? THEN now() ELSE consumed_at END WHERE user_id=?",
    attempts,attempts,MAX_ATTEMPTS,user.getId());
   throw invalid();
  }
  jdbc.update("UPDATE email_verification SET consumed_at=now() WHERE user_id=?",user.getId());
  jdbc.update("UPDATE app_user SET email_verified=TRUE,updated_at=now() WHERE id=?",user.getId());
  return users.findById(user.getId()).orElseThrow(this::invalid);
 }
 @Transactional
 public void checkLoginRateLimit(String email,String ip) {
  rate("login-email:"+(email==null?"":email.trim().toLowerCase()),10);
  rate("login-ip:"+safeIp(ip),20);
 }
 @Transactional(noRollbackFor = ApiException.class)
 public void resend(String email,String ip) {
  if(email==null||email.isBlank()) return;
  users.findByEmailIgnoreCase(email.trim()).filter(u->!u.isEmailVerified())
   .ifPresent(u->sendCode(u,ip));
 }
 private void rate(String key,int limit) {
  Integer count=jdbc.queryForObject("""
   INSERT INTO auth_rate_limit(bucket_key,window_started_at,request_count) VALUES(?,now(),1)
   ON CONFLICT(bucket_key) DO UPDATE SET
   request_count=CASE WHEN auth_rate_limit.window_started_at<now()-interval '15 minutes'
    THEN 1 ELSE auth_rate_limit.request_count+1 END,
   window_started_at=CASE WHEN auth_rate_limit.window_started_at<now()-interval '15 minutes'
    THEN now() ELSE auth_rate_limit.window_started_at END RETURNING request_count
   """,Integer.class,hash(key));
  if(count!=null&&count>limit) throw new ApiException(HttpStatus.TOO_MANY_REQUESTS,"Too many requests. Try again later.");
 }
 private String hash(String value) {
  try { Mac m=Mac.getInstance("HmacSHA256"); m.init(new SecretKeySpec(secret,"HmacSHA256"));
   return HexFormat.of().formatHex(m.doFinal(value.getBytes(StandardCharsets.UTF_8)));
  } catch(Exception e){throw new IllegalStateException("Unable to protect verification data",e);}
 }
 private String safeIp(String ip){return ip==null||ip.isBlank()?"unknown":ip.substring(0,Math.min(ip.length(),64));}
 private ApiException invalid(){return new ApiException(HttpStatus.BAD_REQUEST,
  "Verification could not be completed. Check the details or request a new code.");}
 private record Verification(String hash,Instant expires,int attempts,Instant consumed){}
}