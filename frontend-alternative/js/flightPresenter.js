;(function($, d3, d3zoom, topojson, undefined) {
	'use strict';

	if( typeof $ === 'undefined' || typeof d3 === 'undefined' || typeof topojson === 'undefined' || typeof d3.geo.zoom == 'undefined' ) {
		throw 'Missing dependencies.';
	}

	var Presenter = function(container, options) {
		this.id	   		= options.id;	
		this.width 		= options.width || 500;
		this.height		= options.height || 500;
		this.scale		= options.scale || 245;
		this.sens		= options.sens || 0.25;
		this.projection	= null;
		this.path		= null;
		this.svg		= null;
		this.container	= container || 'body';
	}

	Presenter.prototype.currentFrame = 0;
	Presenter.prototype.maxFrame = 0;
	Presenter.prototype.routes = {};
	Presenter.prototype.slider = null;

	Presenter.prototype.draw = function() {

		var self = this;

		this.projection = d3.geo.orthographic()
    		.scale(this.scale)
    		.clipAngle(90)
    		.translate([this.width/2, this.height/2])
    		.precision(.1);

    	this.path = d3.geo.path()
  			.projection(this.projection);	

  		var zoom = d3zoom()
  			.projection(this.projection)
  			.on('zoom', function(){
  				self.svg.selectAll('path').attr('d', self.path)
  			});

    	this.svg = d3.select(this.container).append("svg")
  			.attr("width", this.width)
  			.attr("height", this.height)
  			.style("cursor", "move")
  			.call(zoom)
			.on("mousedown.zoom", null);

		this.svg.append("path")
  			.datum({type: "Sphere"})
  			.attr("class", "water")
  			.attr("d", this.path)
  			.call(d3.behavior.drag()
    			.origin(function() { var r = self.projection.rotate(); return {x: r[0] / self.sens, y: -r[1] / self.sens}; })
    			.on("drag", function() {
        			d3.selectAll(".point")
          				.transition()
          				.remove()
        
        			var rotate = self.projection.rotate();
        			self.projection.rotate([d3.event.x * self.sens, -d3.event.y * self.sens, rotate[2]]);
        			self.svg.selectAll("path").attr("d", self.path);
  				}));

		this.loadCountries();
		this.loadFlights();

	}

	Presenter.prototype.loadCountries = function() {

		var self = this;

		d3.json('js/countries.topo.json', function(error, world) {
			if (error) throw error;

			var countries = topojson.feature(world, world.objects.countries).features

			world = self.svg.selectAll("path.land")
    			.data(countries)
    			.enter().append("path")
    			.attr("class", "land")
    			.attr("d", self.path)
    			.call(d3.behavior.drag()
	    			.origin(function() { var r = self.projection.rotate(); return {x: r[0] / self.sens, y: -r[1] / self.sens}; })
	    			.on("drag", function() {
	        			d3.selectAll(".point")
	          				.transition()
	          				.remove()
	        
	        			var rotate = self.projection.rotate();
	        			self.projection.rotate([d3.event.x * self.sens, -d3.event.y * self.sens, rotate[2]]);
	        			self.svg.selectAll("path").attr("d", self.path);
	  				}));
		});
	}

	Presenter.prototype.loadFlights = function() {

		var self = this;

		d3.csv("part-00000"+ '?' + Math.floor(Math.random() * 1000), function(error, res) {
			res.forEach(function(data) {
				var obj = {};
				obj[+data.Start] = data.Values.split(' ');
				obj[+data.Start].pop();

				var endFrame = +data.Start + obj[data.Start].length - 1;
				if (self.maxFrame < endFrame) self.maxFrame = endFrame;

				self.routes[data.Id] = obj;
			});
			console.log("MaxFrame: ", self.maxFrame);
			self.createSlider();
		});

	}

	Presenter.prototype.createSlider = function() {

		var sliderScale = d3.scale.linear().domain([0, this.maxFrame]);
		var val = this.slider ? this.slider.value() : 0;

		this.slider = d3.slider()
    		.scale( sliderScale )
    		.on("slide",function(event,value){
    			//console.log(value);
    		})
    		.value(val);

    	d3.select("#slider")
    		.call( this.slider );

	}

	/* RENDERING */
	$(function(){

		var presenter = new Presenter('#globeCanvas', {'width': 700, 'height': 700, 'scale': 275});

		presenter.draw();

	});

}(jQuery, d3, d3.geo.zoom, topojson));