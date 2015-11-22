/**
 * jQuery Roundabout - v1.1
 * http://fredhq.com/projects/roundabout/
 *
 * Moves list-items of enabled ordered and unordered lists long
 * a chosen path. Includes the default "lazySusan" path, that
 * moves items long a spinning turntable.
 *
 * Terms of Use // jQuery Roundabout
 * 
 * Open source under the BSD license
 *
 * Copyright (c) 2010, Fred LeBlanc
 * All rights reserved.
 * 
 * Redistribution and use in source and binary forms, with or without 
 * modification, are permitted provided that the following conditions are met:
 * 
 *   - Redistributions of source code must retain the above copyright
 *     notice, this list of conditions and the following disclaimer.
 *   - Redistributions in binary form must reproduce the above 
 *     copyright notice, this list of conditions and the following 
 *     disclaimer in the documentation and/or other materials provided 
 *     with the distribution.
 *   - Neither the name of the author nor the names of its contributors 
 *     may be used to endorse or promote products derived from this 
 *     software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" 
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE 
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE 
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE 
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR 
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF 
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS 
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN 
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) 
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE 
 * POSSIBILITY OF SUCH DAMAGE.
 */


// creates a default shape to be used for pathing
jQuery.extend({
	roundabout_shape: {
		def: 'lazySusan',
		lazySusan: function(r, a, t) {
			return {
				x: Math.sin(r + a), 
				y: (Math.sin(r + 3*Math.PI/2 + a) / 8) * t, 
				z: (Math.cos(r + a) + 1) / 2,
				scale: (Math.sin(r + Math.PI/2 + a) / 2) + 0.5
			};
		}
	}
});

jQuery.fn.roundabout = function() {
	var options = (typeof arguments[0] != 'object') ? {} : arguments[0];

	// set options and fill in defaults
	options = {
		bearing: (typeof options.bearing == 'undefined') ? 0.0 : jQuery.roundabout_toFloat(options.bearing % 360.0),
		tilt: (typeof options.tilt == 'undefined') ? 0.0 : jQuery.roundabout_toFloat(options.tilt),
		minZ: (typeof options.minZ == 'undefined') ? 100 : parseInt(options.minZ, 10),
		maxZ: (typeof options.maxZ == 'undefined') ? 400 : parseInt(options.maxZ, 10),
		minOpacity: (typeof options.minOpacity == 'undefined') ? 0.40 : jQuery.roundabout_toFloat(options.minOpacity),
		maxOpacity: (typeof options.maxOpacity == 'undefined') ? 1.00 : jQuery.roundabout_toFloat(options.maxOpacity),
		minScale: (typeof options.minScale == 'undefined') ? 0.40 : jQuery.roundabout_toFloat(options.minScale),
		maxScale: (typeof options.maxScale == 'undefined') ? 1.00 : jQuery.roundabout_toFloat(options.maxScale),
		duration: (typeof options.duration == 'undefined') ? 600 : parseInt(options.duration, 10),
		btnNext: options.btnNext || null,
		btnPrev: options.btnPrev || null,
		easing: options.easing || 'swing',
		clickToFocus: (options.clickToFocus !== false),
		focusBearing: (typeof options.focusBearing == 'undefined') ? 0.0 : jQuery.roundabout_toFloat(options.focusBearing % 360.0),
		shape: options.shape || 'lazySusan',
		debug: options.debug || false,
		childSelector: options.childSelector || 'li',
		startingChild: (typeof options.startingChild == 'undefined') ? null : parseInt(options.startingChild, 10),
		reflect: (typeof options.reflect == 'undefined' || options.reflect === false) ? false : true
	};

	// assign things 
	this.each(function(i) {
		var ref = jQuery(this);
		var period = jQuery.roundabout_toFloat(360.0 / ref.children(options.childSelector).length);
		var startingBearing = (options.startingChild === null) ? options.bearing : options.startingChild * period;
		
		// set starting styles
		ref
			.addClass('roundabout-holder')
			.css('padding', 0)
			.css('position', 'relative')
			.css('z-index', options.minZ);
		
		// set starting options
		ref.data('roundabout', {
			'bearing': startingBearing,
			'tilt': options.tilt,
			'minZ': options.minZ,
			'maxZ': options.maxZ,
			'minOpacity': options.minOpacity,
			'maxOpacity': options.maxOpacity,
			'minScale': options.minScale,
			'maxScale': options.maxScale,
			'duration': options.duration,
			'easing': options.easing,
			'clickToFocus': options.clickToFocus,
			'focusBearing': options.focusBearing,
			'animating': 0,
			'childInFocus': -1,
			'shape': options.shape,
			'period': period,
			'debug': options.debug,
			'childSelector': options.childSelector,
			'reflect': options.reflect
		});
				
		// bind click events
		if (options.clickToFocus === true) {
			ref.children(options.childSelector).each(function(i) {
				jQuery(this).click(function(e) {
					var degrees = (options.reflect === true) ? 360.0 - (period * i) : period * i;
					degrees = jQuery.roundabout_toFloat(degrees);
					if (!jQuery.roundabout_isInFocus(ref, degrees)) {
						e.preventDefault();
						if (ref.data('roundabout').animating === 0) {
							ref.roundabout_animateAngleToFocus(degrees);
						}
						return false;
					}
				});
			});
		}
		
		// bind next buttons
		if (options.btnNext) {
			jQuery(options.btnNext).bind('click.roundabout', function(e) {
				e.preventDefault();
				if (ref.data('roundabout').animating === 0) {
					ref.roundabout_animateToNextChild();
				}
				return false;
			});
		}
		
		// bind previous buttons
		if (options.btnPrev) {
			jQuery(options.btnPrev).bind('click.roundabout', function(e) {
				e.preventDefault();
				if (ref.data('roundabout').animating === 0) {
					ref.roundabout_animateToPreviousChild();
				}
				return false;
			});
		}
	});

	// start children
	this.roundabout_startChildren();

	// callback once ready
	if (typeof arguments[1] === 'function') {
		var callback = arguments[1], ref = this;
		setTimeout(function() { callback(ref); }, 0);
	}

	return this;
};

jQuery.fn.roundabout_startChildren = function() {
	this.each(function(i) {
		var ref = jQuery(this);
		var data = ref.data('roundabout');
		var children = ref.children(data.childSelector);
		
		children.each(function(i) {
			var degrees = (data.reflect === true) ? 360.0 - (data.period * i) : data.period * i;

			// apply classes and css first
			jQuery(this)
				.addClass('roundabout-moveable-item')
				.css('position', 'absolute');
			
			// then measure
			jQuery(this).data('roundabout', {
				'startWidth': jQuery(this).width(),
				'startHeight': jQuery(this).height(),
				'startFontSize': parseInt(jQuery(this).css('font-size'), 10),
				'degrees': degrees
			});
		});
		
		ref.roundabout_updateChildPositions();
	});
	return this;
};

jQuery.fn.roundabout_setTilt = function(newTilt) {
	this.each(function(i) {
		jQuery(this).data('roundabout').tilt = newTilt;
		jQuery(this).roundabout_updateChildPositions();
	});
	
	if (typeof arguments[1] === 'function') {
		var callback = arguments[1], ref = this;
		setTimeout(function() { callback(ref); }, 0);
	}
	
	return this;
};

jQuery.fn.roundabout_setBearing = function(newBearing) {
	this.each(function(i) {
		jQuery(this).data('roundabout').bearing = jQuery.roundabout_toFloat(newBearing % 360, 2);
		jQuery(this).roundabout_updateChildPositions();
	});

	if (typeof arguments[1] === 'function') {
		var callback = arguments[1], ref = this;
		setTimeout(function() { callback(ref); }, 0);
	}
	
	return this;
};

jQuery.fn.roundabout_adjustBearing = function(delta) {
	delta = jQuery.roundabout_toFloat(delta);
	if (delta !== 0) {
		this.each(function(i) {
			jQuery(this).data('roundabout').bearing = jQuery.roundabout_getBearing(jQuery(this)) + delta;
			jQuery(this).roundabout_updateChildPositions();
		});
	}
	
	if (typeof arguments[1] === 'function') {
		var callback = arguments[1], ref = this;
		setTimeout(function() { callback(ref); }, 0);
	}

	return this;
};

jQuery.fn.roundabout_adjustTilt = function(delta) {
	delta = jQuery.roundabout_toFloat(delta);
	if (delta !== 0) {
		this.each(function(i) {
			jQuery(this).data('roundabout').tilt = jQuery.roundabout_toFloat(jQuery(this).roundabout_get('tilt') + delta);
			jQuery(this).roundabout_updateChildPositions();
		});
	}
	
	if (typeof arguments[1] === 'function') {
		var callback = arguments[1], ref = this;
		setTimeout(function() { callback(ref); }, 0);
	}

	return this;
};

jQuery.fn.roundabout_animateToBearing = function(bearing) {
	bearing = jQuery.roundabout_toFloat(bearing);
	var currentTime = new Date();
	var duration    = (typeof arguments[1] == 'undefined') ? null : arguments[1];
	var easingType  = (typeof arguments[2] == 'undefined') ? null : arguments[2];
	var passedData  = (typeof arguments[3] !== 'object')   ? null : arguments[3];

	this.each(function(i) {
		var ref = jQuery(this), data = ref.data('roundabout'), timer, easingFn, newBearing;
		var thisDuration = (duration === null) ? data.duration : duration;
		var thisEasingType = (easingType !== null) ? easingType : data.easing || 'swing';

		if (passedData === null) {
			passedData = {
				timerStart: currentTime,
				start: jQuery.roundabout_getBearing(ref),
				totalTime: thisDuration
			};
		}
		timer = currentTime - passedData.timerStart;

		if (timer < thisDuration) {
			data.animating = 1;
			
			if (typeof jQuery.easing.def == 'string') {
				easingFn = jQuery.easing[thisEasingType] || jQuery.easing[jQuery.easing.def];
				newBearing = easingFn(null, timer, passedData.start, bearing - passedData.start, passedData.totalTime);
			} else {
				newBearing = jQuery.easing[thisEasingType]((timer / passedData.totalTime), timer, passedData.start, bearing - passedData.start, passedData.totalTime);
			}
			
			ref.roundabout_setBearing(newBearing, function() { ref.roundabout_animateToBearing(bearing, thisDuration, thisEasingType, passedData); });
		} else {
			bearing = (bearing < 0) ? bearing + 360 : bearing % 360;
			data.animating = 0;
			ref.roundabout_setBearing(bearing);
		}
	});	
	return this;
};

jQuery.fn.roundabout_animateToDelta = function(delta) {
	var duration = arguments[1], easing = arguments[2];
	this.each(function(i) {
		delta = jQuery.roundabout_getBearing(jQuery(this)) + jQuery.roundabout_toFloat(delta);
		jQuery(this).roundabout_animateToBearing(delta, duration, easing);
	});
	return this;
};

jQuery.fn.roundabout_animateToChild = function(childPos) {	
	var duration = arguments[1], easing = arguments[2];	
	this.each(function(i) {
		var ref = jQuery(this), data = ref.data('roundabout');
		if (data.childInFocus !== childPos && data.animating === 0) {		
			var child = jQuery(ref.children(data.childSelector)[childPos]);
			ref.roundabout_animateAngleToFocus(child.data('roundabout').degrees, duration, easing);
		}
	});
	return this;
};

jQuery.fn.roundabout_animateToNearbyChild = function(passedArgs, which) {
	var duration = passedArgs[0], easing = passedArgs[1];
	this.each(function(i) {
		var data     = jQuery(this).data('roundabout');
		var bearing  = jQuery.roundabout_toFloat(360.0 - jQuery.roundabout_getBearing(jQuery(this)));
		var period   = data.period, j = 0, range;
		var reflect  = data.reflect;
		var length   = jQuery(this).children(data.childSelector).length;

		bearing = (reflect === true) ? bearing % 360.0 : bearing;
		
		if (data.animating === 0) {
			// if we're not reflecting and we're moving to next or
			//    we are reflecting and we're moving previous
			if ((reflect === false && which === 'next') || (reflect === true && which !== 'next')) {
				bearing = (bearing === 0) ? 360 : bearing;
							
				// counterclockwise
				while (true && j < length) {
					range = { lower: jQuery.roundabout_toFloat(period * j), upper: jQuery.roundabout_toFloat(period * (j + 1)) };
					range.upper = (j == length - 1) ? 360.0 : range.upper;  // adjust for javascript being bad at floats

					if (bearing <= range.upper && bearing > range.lower) {
						jQuery(this).roundabout_animateToDelta(bearing - range.lower, duration, easing);
						break;
					}
					j++;
				}
			} else {
				// clockwise
				while (true) {
					range = { lower: jQuery.roundabout_toFloat(period * j), upper: jQuery.roundabout_toFloat(period * (j + 1)) };
					range.upper = (j == length - 1) ? 360.0 : range.upper;  // adjust for javascript being bad at floats

					if (bearing >= range.lower && bearing < range.upper) {
						jQuery(this).roundabout_animateToDelta(bearing - range.upper, duration, easing);
						break;
					}
					j++;
				}
			}
		}
	});
	return this;
};

jQuery.fn.roundabout_animateToNextChild = function() {	
	return this.roundabout_animateToNearbyChild(arguments, 'next');
};

jQuery.fn.roundabout_animateToPreviousChild = function() {	
	return this.roundabout_animateToNearbyChild(arguments, 'previous');
};

// moves a given angle to the focus by the shortest means possible
jQuery.fn.roundabout_animateAngleToFocus = function(target) {
	var duration = arguments[1], easing = arguments[2];
	this.each(function(i) {
		var delta = jQuery.roundabout_getBearing(jQuery(this)) - target;
		delta = (Math.abs(360.0 - delta) < Math.abs(0.0 - delta)) ? 360.0 - delta : 0.0 - delta;
		delta = (delta > 180) ? -(360.0 - delta) : delta;
		
		if (delta !== 0) {
			jQuery(this).roundabout_animateToDelta(delta, duration, easing);	
		}
	});
	return this;
};

jQuery.fn.roundabout_updateChildPositions = function() {
	this.each(function(i) {
		var ref = jQuery(this), data = ref.data('roundabout');
		var inFocus = -1;
		var info = {
			bearing: jQuery.roundabout_getBearing(ref),
			tilt: data.tilt,
			stage: { width: Math.floor(ref.width() * 0.9), height: Math.floor(ref.height() * 0.9) },
			animating: data.animating,
			inFocus: data.childInFocus,
			focusBearingRad: jQuery.roundabout_degToRad(data.focusBearing),
			shape: jQuery.roundabout_shape[data.shape] || jQuery.roundabout_shape[jQuery.roundabout_shape.def]
		};
		info.midStage = { width: info.stage.width / 2, height: info.stage.height / 2 };
		info.nudge = { width: info.midStage.width + info.stage.width * 0.05, height: info.midStage.height + info.stage.height * 0.05 };
		info.zValues = { min: data.minZ, max: data.maxZ, diff: data.maxZ - data.minZ };
		info.opacity = { min: data.minOpacity, max: data.maxOpacity, diff: data.maxOpacity - data.minOpacity };
		info.scale = { min: data.minScale, max: data.maxScale, diff: data.maxScale - data.minScale };

		// update child positions
		ref.children(data.childSelector).each(function(i) {
			if (jQuery.roundabout_updateChildPosition(jQuery(this), ref, info, i) && info.animating === 0) {
				inFocus = i;
				jQuery(this).addClass('roundabout-in-focus');
			} else {
				jQuery(this).removeClass('roundabout-in-focus');
			}
		});

		// update status of who is in focus
		if (inFocus !== info.inFocus) {
			jQuery.roundabout_triggerEvent(ref, info.inFocus, 'blur');

			if (inFocus !== -1) {
				jQuery.roundabout_triggerEvent(ref, inFocus, 'focus');
			}

			data.childInFocus = inFocus;
		}
	});	
	return this;	
};

//----------------

jQuery.roundabout_getBearing = function(el) {
	return jQuery.roundabout_toFloat(el.data('roundabout').bearing) % 360;
};

jQuery.roundabout_degToRad = function(degrees) {
	return (degrees % 360.0) * Math.PI / 180.0;
};

jQuery.roundabout_isInFocus = function(el, target) {
	return (jQuery.roundabout_getBearing(el) % 360 === (target % 360));
};

jQuery.roundabout_triggerEvent = function(el, child, eventType) {
	return (child < 0) ? this : jQuery(el.children(el.data('roundabout').childSelector)[child]).trigger(eventType);
};

jQuery.roundabout_toFloat = function(number) {
	number = Math.round(parseFloat(number) * 1000) / 1000;
	return parseFloat(number.toFixed(2));
};

jQuery.roundabout_updateChildPosition = function(child, container, info, childPos) {
	var ref = jQuery(child), data = ref.data('roundabout'), out = [];
	var rad = jQuery.roundabout_degToRad((360.0 - ref.data('roundabout').degrees) + info.bearing);
	
	// adjust radians to be between 0 and Math.PI * 2
	while (rad < 0) {
		rad = rad + Math.PI * 2;
	}
	while (rad > Math.PI * 2) {
		rad = rad - Math.PI * 2;
	}
	
	var factors = info.shape(rad, info.focusBearingRad, info.tilt); // obj with x, y, z, and scale values

	// correct
	factors.scale = (factors.scale > 1) ? 1 : factors.scale;
	factors.adjustedScale = (info.scale.min + (info.scale.diff * factors.scale)).toFixed(4);
	factors.width = (factors.adjustedScale * data.startWidth).toFixed(4);
	factors.height = (factors.adjustedScale * data.startHeight).toFixed(4);
	
	// alter item
	ref
		.css('left', ((factors.x * info.midStage.width + info.nudge.width) - factors.width / 2.0).toFixed(1) + 'px')
		.css('top', ((factors.y * info.midStage.height + info.nudge.height) - factors.height / 2.0).toFixed(1) + 'px')
		.css('width', factors.width + 'px')
		.css('height', factors.height + 'px')
		.css('opacity', (info.opacity.min + (info.opacity.diff * factors.scale)).toFixed(2))
		.css('z-index', Math.round(info.zValues.min + (info.zValues.diff * factors.z)))
		.css('font-size', (factors.adjustedScale * data.startFontSize).toFixed(2) + 'px')
		.attr('current-scale', factors.adjustedScale);
	
	if (container.data('roundabout').debug === true) {
		out.push('<div style="font-weight: normal; font-size: 10px; padding: 2px; width: ' + ref.css('width') + '; background-color: #ffc;">');
		out.push('<strong style="font-size: 12px; white-space: nowrap;">Child ' + childPos + '</strong><br />');
		out.push('<strong>left:</strong> ' + ref.css('left') + '<br /><strong>top:</strong> ' + ref.css('top') + '<br />');
		out.push('<strong>width:</strong> ' + ref.css('width') + '<br /><strong>opacity:</strong> ' + ref.css('opacity') + '<br />');
		out.push('<strong>z-index:</strong> ' + ref.css('z-index') + '<br /><strong>font-size:</strong> ' + ref.css('font-size') + '<br />');
		out.push('<strong>scale:</strong> ' + ref.attr('current-scale'));
		out.push('</div>');
		
		ref.html(out.join(''));
	}

	return jQuery.roundabout_isInFocus(container, ref.data('roundabout').degrees);
};