<%@ taglib prefix="form" uri="http://www.springframework.org/tags/form" %>
<html>
<head>
    <meta http-equiv="Content-Type" content="text/html; charset=ISO-8859-1">
    <title>Home</title>
</head>
<body>

JSP home :)

<form:form method="POST" modelAttribute="user">
    <form:label path="email">Email: </form:label>
    <form:input path="email" type="text"/>
    <form:label path="password">Password: </form:label>
    <form:input path="password" type="password"/>
    <input type="submit" value="Submit"/>
</form:form>

</body>
</html>