(function(){
    /* up: 38 down: 40 left: 37 right: 39 a: 65 b: 66 */
    var konamiChain = [38,38,40,40,37,39,37,39,66,65], enteredChain = [], konamiCallback;
    function chain(code){
        var nextCode = konamiChain[enteredChain.length];
        if(code==nextCode){
            enteredChain.push(code);
        }else{
            enteredChain = [];
        }
        if(enteredChain.length==konamiChain.length){
            enteredChain = [];
            if('function'===typeof konamiCallback) konamiCallback();
        }
    }
    window.doKonamiListen = function(callback){
        konamiCallback = callback;
        document.addEventListener('keyup', function (evt){
            chain(evt.keyCode);
        }, false);
    };
})();