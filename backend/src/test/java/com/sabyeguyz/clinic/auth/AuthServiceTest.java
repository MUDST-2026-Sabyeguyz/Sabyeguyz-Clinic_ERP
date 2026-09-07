package com.sabyeguyz.clinic.auth;
import static org.junit.jupiter.api.Assertions.*; import static org.mockito.ArgumentMatchers.any; import static org.mockito.Mockito.*; import java.util.Optional; import org.junit.jupiter.api.Test; import org.junit.jupiter.api.extension.ExtendWith; import org.mockito.*;
@ExtendWith(org.mockito.junit.jupiter.MockitoExtension.class) class AuthServiceTest { @Mock AppUserRepository repo;
 @Test void registerHashesPasswordAndDefaultsRole(){ when(repo.findByUsername("alice")).thenReturn(Optional.empty()); when(repo.save(any())).thenAnswer(i->i.getArgument(0)); AppUser u=new AuthService(repo).register("alice","password1",Role.STAFF); assertEquals(Role.STAFF,u.getRole()); assertNotEquals("password1",u.getPasswordHash()); verify(repo).save(any()); }
 @Test void loginRejectsWrongPassword(){ when(repo.findByUsername("alice")).thenReturn(Optional.of(new AppUser("alice","$2a$10$7EqJtq98hPqEX7fNZaFWoO1cVhX4j7P6cQ0Q8Z9f3X7mX8Qw8W7Qe",Role.STAFF))); assertThrows(IllegalArgumentException.class,()->new AuthService(repo).login("alice","wrong")); }
}
