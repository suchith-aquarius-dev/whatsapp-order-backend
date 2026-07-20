package com.example.waorder.controller;

import com.example.waorder.config.WhatsAppProperties;
import com.example.waorder.model.Order;
import com.example.waorder.repository.OrderRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

@Slf4j
@Controller
@RequestMapping("/admin")
public class AdminController {

    private final OrderRepository orderRepository;
    private final WhatsAppProperties whatsAppProperties;

    public AdminController(OrderRepository orderRepository, WhatsAppProperties whatsAppProperties) {
        this.orderRepository = orderRepository;
        this.whatsAppProperties = whatsAppProperties;
    }

    // New endpoint for the main admin dashboard
    @GetMapping
    public String mainAdminDashboard() {
        return "admin-main-dashboard";
    }

    @GetMapping("/orders") // Renamed from /dashboard to /orders
    public String adminOrders(Model model,
                                 @RequestParam(value = "filterType", defaultValue = "today") String filterType,
                                 @RequestParam(value = "date", required = false) LocalDate date,
                                 @RequestParam(value = "weekStart", required = false) LocalDate weekStart,
                                 @RequestParam(value = "month", required = false) String month,
                                 @RequestParam(value = "orderStatus", required = false) Order.OrderStatus orderStatus) {

        List<Order> orders;
        LocalDate startDate = null;
        LocalDate endDate = null;

        switch (filterType) {
            case "today":
                startDate = LocalDate.now();
                endDate = LocalDate.now();
                break;
            case "date":
                if (date != null) {
                    startDate = date;
                    endDate = date;
                } else {
                    startDate = LocalDate.now();
                    endDate = LocalDate.now();
                }
                break;
            case "week":
                if (weekStart != null) {
                    startDate = weekStart;
                    endDate = weekStart.plusDays(6);
                } else {
                    // Default to current week
                    startDate = LocalDate.now().with(TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY));
                    endDate = startDate.plusDays(6);
                }
                break;
            case "month":
                if (month != null && !month.isEmpty()) {
                    // month format "YYYY-MM"
                    int year = Integer.parseInt(month.substring(0, 4));
                    int monthNum = Integer.parseInt(month.substring(5, 7));
                    startDate = LocalDate.of(year, monthNum, 1);
                    endDate = startDate.with(TemporalAdjusters.lastDayOfMonth());
                } else {
                    // Default to current month
                    startDate = LocalDate.now().with(TemporalAdjusters.firstDayOfMonth());
                    endDate = LocalDate.now().with(TemporalAdjusters.lastDayOfMonth());
                }
                break;
            default:
                startDate = LocalDate.now();
                endDate = LocalDate.now();
                break;
        }

        if (orderStatus != null) {
            orders = orderRepository.findByDeliveryDateBetweenAndStatusOrderByDeliveryDateAscDeliveryTimeAsc(startDate, endDate, orderStatus);
        } else {
            orders = orderRepository.findByDeliveryDateBetweenOrderByDeliveryDateAscDeliveryTimeAsc(startDate, endDate);
        }

        // Get distinct statuses from the database
        List<Order.OrderStatus> distinctStatuses = orderRepository.findDistinctStatuses();
        // Combine with all possible enum values and sort for consistent display
        Set<Order.OrderStatus> allPossibleAndExistingStatuses = new TreeSet<>(Arrays.asList(Order.OrderStatus.values()));
        allPossibleAndExistingStatuses.addAll(distinctStatuses);


        model.addAttribute("orders", orders);
        model.addAttribute("filterType", filterType);
        model.addAttribute("selectedDate", date);
        model.addAttribute("selectedWeekStart", startDate); // Display the actual start of the week
        model.addAttribute("selectedMonth", month != null ? month : LocalDate.now().toString().substring(0, 7)); // YYYY-MM
        model.addAttribute("whatsappNumber", whatsAppProperties.getWhatsappNumber());
        model.addAttribute("orderStatuses", allPossibleAndExistingStatuses); // Use the combined and sorted list
        model.addAttribute("selectedOrderStatus", orderStatus); // Pass selected status back to view

        return "admin-orders"; // Return the renamed template
    }
}
