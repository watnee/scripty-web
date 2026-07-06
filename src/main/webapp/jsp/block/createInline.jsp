<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@page contentType="text/html" pageEncoding="UTF-8"%>
<tr draggable="true">
    <td class="block-left-controls">
        <a hx-get="${pageContext.request.contextPath}/block/createInline?sceneId=${viewModel.sceneId}" hx-target="closest tr" hx-swap="afterend" hx-boost="false" role="button" class="create-below">+</a>
        <span class="drag-handle">&#8942;&#8942;</span>
    </td>
    <td class="block-content">
        <form hx-post="${pageContext.request.contextPath}/block/createInline" hx-target="closest tr" hx-swap="outerHTML" hx-trigger="input delay:500ms from:find textarea, keydown[key=='Enter'&&!event.shiftKey] from:find textarea, createNext">
            <input type="hidden" name="sceneId" value="${viewModel.sceneId}" />
            <textarea spellcheck="true" rows="1" style="width: 100%; height: 1.5em; min-height: 1.5em; padding: 0.25em;" name="content" autofocus onkeydown="if(event.key==='Enter'&&!event.shiftKey)event.preventDefault()" _="on blur if my value.trim() is '' and (closest <table/>).querySelectorAll('tr').length is greater than 1 remove closest <tr/>"></textarea>
        </form>
    </td>
    <td></td>
</tr>
