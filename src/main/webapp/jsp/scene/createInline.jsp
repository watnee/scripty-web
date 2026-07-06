<%@page contentType="text/html" pageEncoding="UTF-8"%>
<tr id="create-scene-row">
    <td colspan="2">
        <form hx-post="${pageContext.request.contextPath}/scene/createInline" hx-target="#create-scene-row" hx-swap="outerHTML" hx-trigger="keyup[key=='Enter'] from:find input[name='name'], blur[target.value.trim()!=''] from:find input[name='name']">
            <input type="hidden" name="projectId" value="${projectId}" />
            <input type="text" spellcheck="true" name="name" autofocus placeholder="Scene name" _="on blur if my value.trim() is '' remove #create-scene-row then show #create-scene-btn" />
        </form>
    </td>
</tr>
