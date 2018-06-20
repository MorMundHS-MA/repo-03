var ipLogin;
var ipRegister;
var ipChat;

//gets the ips from the local storage or uses the standard ips.
function loadConfig() {
    if (localStorage.length != 0) {
        ipLogin = localStorage.ipLogin;
        ipChat = localStorage.ipChat;
        ipRegister = localStorage.ipRegister;
    } else {
        ipLogin = "http://localhost:5001";
        ipChat = "http://localhost:5000";
        ipRegister = "http://localhost:5002";
    }
    $("#inputIpLogin").val(ipLogin);
    $("#inputIpChat").val(ipChat);
    $("#inputIpRegister").val(ipRegister);
}

//resets all ip variables in the local storage.
function resetConfig(){
    localStorage.removeItem("ipLogin");
    localStorage.removeItem("ipChat");
    localStorage.removeItem("ipRegister");
    loadConfig();
    return false;
}

//saves the ips from the input fields to the local storage.
function saveConfig() {
    localStorage.setItem("ipLogin", $("#inputIpLogin").val());
    localStorage.setItem("ipChat", $("#inputIpChat").val());
    localStorage.setItem("ipRegister", $("#inputIpRegister").val());
    $(".card-container2").css({
        "left": "-100%"
    });
    return false;
}