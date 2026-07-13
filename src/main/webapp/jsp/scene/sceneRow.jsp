<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@page contentType="text/html" pageEncoding="UTF-8"%>
<tr>
    <td><a href="${pageContext.request.contextPath}/scene/show?id=${scene.id}" style="text-transform: uppercase">${scene.name}</a></td>
    <td>
        <span class="nowrap">
            <a href="${pageContext.request.contextPath}/scene/moveDown?id=${scene.id}" class="move-down" role="button">&#8595;</a>
            <a href="${pageContext.request.contextPath}/scene/moveUp?id=${scene.id}" class="move-up" role="button">&#8593;</a>
        </span>
    </td>
</tr>
