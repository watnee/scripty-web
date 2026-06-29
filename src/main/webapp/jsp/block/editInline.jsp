<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@page contentType="text/html" pageEncoding="UTF-8"%>
<form hx-post="${pageContext.request.contextPath}/block/editInline" hx-target="closest .block-content" hx-swap="innerHTML" hx-trigger="input delay:500ms from:find textarea, change from:find select">
    <input type="hidden" name="id" value="${commandModel.id}" />
    <input type="hidden" name="sceneId" value="${commandModel.sceneId}" />
    <label>
        Content
        <textarea spellcheck="true" rows="25" cols="30" name="content">${commandModel.content}</textarea>
    </label>
    <label>
        Character
        <select name="personId">
            <option value="">No character</option>
            <c:forEach items="${viewModel.persons}" var="person">
                <option value="${person.id}" <c:if test="${person.id == commandModel.personId}">selected</c:if>>${person.name}</option>
            </c:forEach>
        </select>
    </label>
</form>
