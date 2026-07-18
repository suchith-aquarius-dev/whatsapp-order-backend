package com.example.waorder.controller;

import com.example.waorder.service.DeliverySettingsService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Controller
@RequestMapping("/admin/delivery-settings")
@Slf4j
public class DeliverySettingsController {

    private final DeliverySettingsService deliverySettingsService;

    public DeliverySettingsController(DeliverySettingsService deliverySettingsService) {
        this.deliverySettingsService = deliverySettingsService;
    }

    @GetMapping
    public String showSettings(Model model) {
        model.addAttribute("holidays", deliverySettingsService.getAllHolidays());
        model.addAttribute("weeklyOffDays", deliverySettingsService.getWeeklyOffDays());
        model.addAttribute("allDaysOfWeek", DayOfWeek.values());
        return "delivery-settings";
    }

    @PostMapping("/holidays/add")
    public String addHoliday(@RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate date,
                              @RequestParam(required = false) String reason,
                              RedirectAttributes redirectAttributes) {
        try {
            deliverySettingsService.addHoliday(date, reason);
            redirectAttributes.addFlashAttribute("message", "Holiday added - customers won't be able to select this date.");
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        } catch (Exception e) {
            log.error("Failed to add delivery holiday", e);
            redirectAttributes.addFlashAttribute("error", "Failed to add holiday: " + e.getMessage());
        }
        return "redirect:/admin/delivery-settings";
    }

    @PostMapping("/holidays/delete/{id}")
    public String deleteHoliday(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            deliverySettingsService.deleteHoliday(id);
            redirectAttributes.addFlashAttribute("message", "Holiday removed.");
        } catch (Exception e) {
            log.error("Failed to delete delivery holiday {}", id, e);
            redirectAttributes.addFlashAttribute("error", "Failed to remove holiday: " + e.getMessage());
        }
        return "redirect:/admin/delivery-settings";
    }

    @PostMapping("/weekly-off")
    public String updateWeeklyOffDays(@RequestParam(required = false) List<DayOfWeek> weeklyOffDays,
                                       RedirectAttributes redirectAttributes) {
        try {
            Set<DayOfWeek> days = weeklyOffDays == null ? new HashSet<>() : new HashSet<>(weeklyOffDays);
            deliverySettingsService.updateWeeklyOffDays(days);
            redirectAttributes.addFlashAttribute("message", "Weekly off days updated.");
        } catch (Exception e) {
            log.error("Failed to update weekly off days", e);
            redirectAttributes.addFlashAttribute("error", "Failed to update weekly off days: " + e.getMessage());
        }
        return "redirect:/admin/delivery-settings";
    }
}
