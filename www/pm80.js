var exec = require('cordova/exec');

var PM80 = function() {};

PM80.activate = function(success, error) {
	exec(success, error, 'PM80', 'MSR_activateReader', []);
};

PM80.deactivate = function(success, error) {
	exec(success, error, 'PM80', 'MSR_deactivateReader', []);
};

PM80.swipe = function (success, error) {
	exec(success, error, 'PM80', 'MSR_swipe', []);
};

PM80.stopSwipe = function (success, error) {
	exec(success, error, 'PM80', 'MSR_stopSwipe', []);
};

PM80.fireEvent = function (event, data) {
	var customEvent = new CustomEvent(event, { 'detail': data} );
	window.dispatchEvent(customEvent);
};

PM80.on = function (event, callback, scope) {
	window.addEventListener(event, callback);
};
PM80.off = function (event, callback, scope) {
	window.removeEventListener(event, callback);
};
module.exports = PM80;

/*
 * Polyfill for adding CustomEvent -- Copy uncommented lines below into your
 * application if you get  Reference Error: CustomEvent is undefined
 * see : https://developer.mozilla.org/fr/docs/Web/API/CustomEvent,
         http://stackoverflow.com/questions/25579986/
	if (!window.CustomEvent) { // Create only if it doesn't exist
	    (function () {
	        function CustomEvent ( event, params ) {
	            params = params || { bubbles: false, cancelable: false, detail: undefined };
	            var evt = document.createEvent( 'CustomEvent' );
	            evt.initCustomEvent( event, params.bubbles, params.cancelable, params.detail );
	            return evt;
	        };
	
	        CustomEvent.prototype = window.Event.prototype;
	
	        window.CustomEvent = CustomEvent;
	    })();
	}
*/
