<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@page contentType="text/html" pageEncoding="UTF-8"%>
<form hx-post="${pageContext.request.contextPath}/block/editInline" hx-target="closest .block-content" hx-swap="innerHTML" hx-trigger="input delay:500ms from:find textarea, change from:find select">
    <input type="hidden" name="id" value="${commandModel.id}" />
    <input type="hidden" name="sceneId" value="${commandModel.sceneId}" />
    <p>
        <button type="button" onclick="this.closest('form').querySelector('textarea').focus(); document.execCommand('undo')">&#8592; Undo</button>
        <button type="button" onclick="this.closest('form').querySelector('textarea').focus(); document.execCommand('redo')">Redo &#8594;</button>
    </p>
    <textarea spellcheck="true" rows="1" style="width: 100%; height: 1.5em; min-height: 1.5em; padding: 0.25em;" name="content" autofocus>${commandModel.content}</textarea>
    <input type="hidden" name="personId" value="${commandModel.personId}" />
</form>
