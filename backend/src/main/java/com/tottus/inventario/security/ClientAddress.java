package com.tottus.inventario.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

public final class ClientAddress {
    private ClientAddress() { }

    /** Uses the socket peer only; forwarded headers are ignored unless a trusted proxy layer is configured. */
    public static String currentRemoteAddress() {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (!(attributes instanceof ServletRequestAttributes servletAttributes)) return "No disponible";
        HttpServletRequest request = servletAttributes.getRequest();
        String address = request.getRemoteAddr();
        return address == null || address.isBlank() ? "No disponible" : address;
    }
}
