var ipLogin;
var ipRegister;
var ipChat;
function loadConfig() {
    $.ajax({
        url: 'js/config.txt',
        type: 'GET',
        success: function (result) {
            var ips = result.split(";");
            ipLogin = ips[0].substring("ipLogin:".length);
            ipChat = ips[1].substring("ipChat:".length + 1);
            ipRegister = ips[2].substring("ipRegister:".length + 1);
        }
    });
}


function saveConfig(){
    $.ajax
		({
			type: "POST",
			url: 'js/write.php',
			data:{message: "ipLogin:"+$("#inputIpLogin").val()+";\nipChat:"+$("#inputIpChat").val()+";\nipRegister:"+$("#inputIpRegister").val()+";", file: 'config.txt'},
			success: function(data){
				alert(data);
			},

		   
			error:function(){
			  alert("es ist ein Fehler aufgetreten");
		   }
	});
}