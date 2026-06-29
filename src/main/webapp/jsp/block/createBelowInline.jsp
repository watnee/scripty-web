<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@page contentType="text/html" pageEncoding="UTF-8"%>
<tr>
    <td class="drag-handle"></td>
    <td class="block-content">
        <form hx-post="${pageContext.request.contextPath}/block/createBelowInline" hx-target="closest tr" hx-swap="outerHTML" hx-trigger="input delay:500ms from:find textarea, keydown[key=='Enter'&&!event.shiftKey] from:find textarea, createNext">
            <input type="hidden" name="id" value="${blockId}" />
            <textarea spellcheck="true" rows="25" cols="30" name="content" autofocus onkeydown="if(event.key==='Enter'&&!event.shiftKey)event.preventDefault()" _="on blur if my value.trim() is '' remove closest <tr/>"></textarea>
            <label>
                Character
                <select name="personId">
                    <option value="">No character</option>
                    <c:forEach items="${viewModel.persons}" var="person">
                        <option value="${person.id}">${person.name}</option>
                    </c:forEach>
                </select>
            </label>
        </form>
    </td>
    <td>
        <span class="nowrap">
            <a role="button" class="create-below" _="on click trigger createNext on closest <tr/>'s querySelector('form')">+</a>
        </span>
    </td>
</tr>
