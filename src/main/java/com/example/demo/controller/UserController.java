package com.example.demo.controller;

import com.example.demo.dto.ProfileDTO;
import com.example.demo.entity.User;
import com.example.demo.entity.UserRole;
import com.example.demo.repository.UserRepository;

import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;

@Controller
public class UserController {

    @Autowired
    private UserRepository userRepository;

    private static final String DEFAULT_PROFILE_PATH = "/images/default.png";

    // Show registration form
    @GetMapping("/register")
    public String showRegisterForm(Model model) {
        model.addAttribute("user", new User());
        return "nonReact/register"; // View to be created later
    }

    // Handle registration form submission
    @PostMapping("/register")
    public String processRegister(@ModelAttribute("user") User user, Model model) {
    	
    	
    	user.setUsername(user.getUsername().trim());
        user.setEmail(user.getEmail().trim().toLowerCase());
    	
        // Manual length validation
        if (user.getUsername().length() > 254) {
            model.addAttribute("error", "Username must be less than 255 characters.");
            return "nonReact/register";
        }

        if (user.getEmail().length() > 254) {
            model.addAttribute("error", "Email must be less than 255 characters.");
            return "nonReact/register";
        }

        // Uniqueness checks
        if (userRepository.existsByUsername(user.getUsername())) {
            model.addAttribute("error", "Username already taken.");
            return "nonReact/register";
        }
        if (userRepository.existsByEmail(user.getEmail())) {
            model.addAttribute("error", "Email already registered.");
            return "nonReact/register";
        }

        // Default values
        user.setRole(UserRole.visitor);
        user.setProfilePath(DEFAULT_PROFILE_PATH);
        user.setCreatedAt(LocalDateTime.now());

        // Hash password
        String hashedPassword = org.springframework.security.crypto.bcrypt.BCrypt
            .hashpw(user.getPassword(), org.springframework.security.crypto.bcrypt.BCrypt.gensalt());
        user.setPassword(hashedPassword);

        userRepository.save(user);

        model.addAttribute("success", "Account created successfully.");
        return "redirect:/login";
    }

    
    @GetMapping("/login")
    public String showLoginForm(Model model) {
        model.addAttribute("user", new User()); // We only need username and password
        return "nonReact/login"; // View to be created next
    }

    @PostMapping("/login")
    public String processLogin(@RequestParam("usnOrEmail") String usnOrEmail,
                               @RequestParam("password") String password,
                               Model model,
                               HttpSession session) {

        User dbUser = userRepository.findByUsername(usnOrEmail);
        if (dbUser == null) {
            dbUser = userRepository.findByEmail(usnOrEmail);
        }

        if (dbUser == null) {
            model.addAttribute("error", "Account not found.");
            return "nonReact/login";
        }

        boolean passwordMatch = org.springframework.security.crypto.bcrypt.BCrypt
            .checkpw(password, dbUser.getPassword());

        if (!passwordMatch) {
            model.addAttribute("error", "Incorrect password.");
            return "nonReact/login";
        }

        // Login successful
        session.setAttribute("loggedInUser", dbUser);
        return "redirect:/dashboard";
    }

    
    @GetMapping("/dashboard")
    public String dashboard(HttpSession session, Model model) {
        User user = (User) session.getAttribute("loggedInUser");
        if (user == null) return "redirect:/login";

        model.addAttribute("username", user.getUsername());
        return "nonReact/dashboard";
    }
    
    @GetMapping("/logout")
    public String logout(HttpSession session) {
        session.invalidate();
        return "redirect:/login";
    }



    //Rest

@GetMapping("/check-username")
@ResponseBody
public boolean checkUsername(@RequestParam("username") String username) {
    return userRepository.existsByUsername(username);
}

@GetMapping("/check-email")
@ResponseBody
public boolean checkEmail(@RequestParam("email") String email) {
    return userRepository.existsByEmail(email);
}

@GetMapping("/profile/{userId}")
public String showUserProfile(@PathVariable Integer userId, Model model) {
    User user = userRepository.findByUserId(userId);
    if (user == null) {
        // Handle case when user is not found
        return "error"; // You should create an error.html page
    }
    model.addAttribute("user", user);
    return "nonReact/profile";
}

//Edit profile form
@GetMapping("/profile/{userId}/edit")
public String showEditProfileForm(@PathVariable Integer userId, Model model) {
    User user = userRepository.findByUserId(userId);
    if (user == null) {
        return "error";
    }
    
    ProfileDTO profileDTO = new ProfileDTO();
    profileDTO.setUserId(user.getUserId());
    profileDTO.setEmail(user.getEmail());
    profileDTO.setProfilePath(user.getProfilePath());
    
    model.addAttribute("profileDTO", profileDTO);
    return "nonReact/editProfile";
}

// Update profile
@PostMapping("/profile/{userId}/edit")
public String updateProfile(
        @PathVariable Integer userId,
        @ModelAttribute("profileDTO") ProfileDTO profileDTO,
        @RequestParam("profileImage") MultipartFile file) {
    
    User user = userRepository.findByUserId(userId);
    if (user == null) {
        return "error";
    }

    // Handle profile image upload
    if (!file.isEmpty()) {
        try {
            String uploadDir = "uploads/";
            String fileName = System.currentTimeMillis() + "_" + file.getOriginalFilename();
            Path uploadPath = Paths.get(uploadDir);
            
            if (!Files.exists(uploadPath)) {
                Files.createDirectories(uploadPath);
            }
            
            Path filePath = uploadPath.resolve(fileName);
            file.transferTo(filePath.toFile());
            profileDTO.setProfilePath("/" + uploadDir + fileName);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // Update user details
    user.setEmail(profileDTO.getEmail());
    if (profileDTO.getPassword() != null && !profileDTO.getPassword().isEmpty()) {
        user.setPassword(profileDTO.getPassword());
    }
    if (profileDTO.getProfilePath() != null) {
        user.setProfilePath(profileDTO.getProfilePath());
    }
    user.setModifiedAt(LocalDateTime.now());
    
    userRepository.save(user);
    return "redirect:/profile/" + userId;
}
}

    
    

