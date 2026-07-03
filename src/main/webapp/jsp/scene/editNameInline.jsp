<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@page contentType="text/html" pageEncoding="UTF-8"%>
<form hx-post="${pageContext.request.contextPath}/scene/editNameInline" hx-target="closest .scene-name-wrap" hx-swap="innerHTML" hx-trigger="input delay:500ms from:find input, change from:find input">
    <input type="hidden" name="id" value="${commandModel.id}" />
    <input type="hidden" name="projectId" value="${commandModel.projectId}" />
    <input type="text" name="name" value="${commandModel.name}" style="text-transform: uppercase; font-size: inherit; font-weight: inherit" autofocus _="on keydown[key=='Enter'] halt the event then blur() me then send blur to me" />
</form>