package com.sabyeguyz.clinic.auth;
import jakarta.persistence.*; import java.time.Instant;
@Entity @Table(name="app_users") public class AppUser {
 @Id @GeneratedValue(strategy=GenerationType.SEQUENCE,generator="app_user_gen") @SequenceGenerator(name="app_user_gen",sequenceName="app_user_id_seq",allocationSize=1) private Long id;
 @Column(nullable=false,unique=true) private String username; @Column(unique=true) private String email; @Column(name="password_hash",nullable=false) private String passwordHash; @Enumerated(EnumType.STRING) private Role role; private boolean active=true; @Column(name="created_at") private Instant createdAt=Instant.now();
 protected AppUser(){} public AppUser(String username,String passwordHash,Role role){this(username,null,passwordHash,role);} public AppUser(String username,String email,String passwordHash,Role role){this.username=username;this.email=email;this.passwordHash=passwordHash;this.role=role;} public Long getId(){return id;} public String getUsername(){return username;} public String getEmail(){return email;} public String getPasswordHash(){return passwordHash;} public Role getRole(){return role;} public boolean isActive(){return active;}
}
