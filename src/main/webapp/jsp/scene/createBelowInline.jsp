<%@page contentType="text/html" pageEncoding="UTF-8"%>
<div id="create-scene-below-form">
    <form action="${pageContext.request.contextPath}/scene/createBelowInline" method="POST" hx-boost="false">
        <input type="hidden" name="id" value="${sceneId}" />
        <input type="text" spellcheck="true" name="name" autofocus placeholder="" _="on blur if my value.trim() is '' remove #create-scene-below-form then show #create-scene-btn" />
    </form>
</div>
