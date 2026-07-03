<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@page contentType="text/html" pageEncoding="UTF-8"%>
<span class="scene-name-display" hx-get="${pageContext.request.contextPath}/scene/editNameInline?id=${scene.id}" hx-trigger="click" hx-target="closest .scene-name-wrap" hx-swap="innerHTML" style="cursor: pointer; text-transform: uppercase">${scene.name}</span>