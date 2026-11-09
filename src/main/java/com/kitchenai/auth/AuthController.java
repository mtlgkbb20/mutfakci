package com.kitchenai.auth;
import com.kitchenai.common.ApiException;
import com.kitchenai.membership.EntitlementService;
import com.kitchenai.user.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/v1/auth")
public class AuthController {
 private static final String GENERIC="If the address can be registered, a verification email will be sent.";
 private final UserRepository users; private final PasswordEncoder encoder; private final JwtService jwt;
 private final EntitlementService entitlements; private final EmailVerificationService verification;
 public AuthController(UserRepository users,PasswordEncoder encoder,JwtService jwt,
  EntitlementService entitlements,EmailVerificationService verification) {
  this.users=users; this.encoder=encoder; this.jwt=jwt; this.entitlements=entitlements; this.verification=verification;
 }
 public record Register(@Email @NotBlank String email,@Size(min=12,max=128) String password,@NotBlank @Size(max=120) String displayName){}
 public record Login(@Email @NotBlank String email,@NotBlank String password){}
 public record VerifyEmail(@Email @NotBlank String email,@Pattern(regexp="\\d{6}") String code){}
 public record ResendCode(@Email @NotBlank String email){}
 @Transactional @PostMapping("/register") @ResponseStatus(HttpStatus.ACCEPTED)
 public Map<String,Object> register(@Valid @RequestBody Register r,HttpServletRequest request) {
  String email=r.email().trim().toLowerCase(Locale.ROOT);
  Optional<User> existing=users.findByEmailIgnoreCase(email);
  if(existing.isPresent()) {
   if(!existing.get().isEmailVerified()) try {verification.sendCode(existing.get(),request.getRemoteAddr());}
    catch(ApiException ignored) {}
   return Map.of("message",GENERIC);
  }
  User user=users.save(new User(email,encoder.encode(r.password()),r.displayName().trim()));
  entitlements.ensureMembership(user.getId());
  verification.sendCode(user,request.getRemoteAddr());
  return Map.of("message",GENERIC);
 }
 @PostMapping("/verify-email")
 public Map<String,Object> verifyEmail(@Valid @RequestBody VerifyEmail r,HttpServletRequest request) {
  return response(verification.verify(r.email(),r.code(),request.getRemoteAddr()));
 }
 @PostMapping("/resend-verification") @ResponseStatus(HttpStatus.ACCEPTED)
 public Map<String,Object> resend(@Valid @RequestBody ResendCode r,HttpServletRequest request) {
  try {verification.resend(r.email(),request.getRemoteAddr());} catch(ApiException ignored) {}
  return Map.of("message",GENERIC);
 }
 @PostMapping("/login")
 public Map<String,Object> login(@Valid @RequestBody Login r) {
  User user=users.findByEmailIgnoreCase(r.email().trim())
   .filter(u->encoder.matches(r.password(),u.getPasswordHash()))
   .orElseThrow(()->new ApiException(HttpStatus.UNAUTHORIZED,"Invalid credentials"));
  if(!user.isEmailVerified()) throw new ApiException(HttpStatus.FORBIDDEN,"Verify your email before signing in.");
  return response(user);
 }
 @GetMapping("/me")
 public Map<String,Object> me(@org.springframework.security.core.annotation.AuthenticationPrincipal UUID id) {
  User user=users.findById(id).orElseThrow(()->new ApiException(HttpStatus.UNAUTHORIZED,"Authentication required"));
  if(!user.isEmailVerified()) throw new ApiException(HttpStatus.FORBIDDEN,"Email verification required.");
  return userResponse(user);
 }
 private Map<String,Object> response(User u){return Map.of("token",jwt.create(u.getId()),"user",userResponse(u));}
 private Map<String,Object> userResponse(User u){return Map.of("id",u.getId(),"email",u.getEmail(),"displayName",u.getDisplayName(),"isAdmin",u.isAdmin());}
}