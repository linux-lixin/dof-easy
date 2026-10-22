(function($) {

	"use strict";

	// 让 SPA 场景可以重复初始化（Vue 路由切换后 DOM 变化）
	function legacyInit() {
		//Update Header Style and Scroll to Top
		function headerStyle() {
			if($('.main-header').length){
				var windowpos = $(window).scrollTop();
				var siteHeader = $('.main-header');
				var scrollLink = $('.scroll-to-top');
				var sticky_header = $('.main-header .sticky-header');
				if (windowpos > 100) {
					siteHeader.addClass('fixed-header');
					sticky_header.addClass("animated slideInDown");
					scrollLink.fadeIn(300);
				} else {
					siteHeader.removeClass('fixed-header');
					sticky_header.removeClass("animated slideInDown");
					scrollLink.fadeOut(300);
				}
			}
		}

		//Hide Loading Box (Preloader)
		function handlePreloader() {
			if($('.preloader').length){
				$('body').addClass('page-loaded');
				$('.preloader').delay(1000).fadeOut(300);
			}
		}

		headerStyle();

		//Submenu Dropdown Toggle
		if($('.main-header li.dropdown ul').length){
			// 避免重复追加 dropdown-btn
			$('.main-header .navigation li.dropdown').each(function(){
				if ($(this).children('.dropdown-btn').length === 0) {
					$(this).append('<div class="dropdown-btn"><span class="fa fa-angle-right"></span></div>');
				}
			});
		}

		//Mobile Nav Hide Show
		if($('.mobile-menu').length){
			try {
				$('.mobile-menu .menu-box').mCustomScrollbar();
			} catch (e) {
				// ignore
			}

			// 清空后重建，避免重复 append
			$('.mobile-menu .menu-box .menu-outer').empty();
			$('.sticky-header .main-menu').empty();

			var mobileMenuContent = $('.main-header .nav-outer .main-menu').html();
			$('.mobile-menu .menu-box .menu-outer').append(mobileMenuContent);
			$('.sticky-header .main-menu').append(mobileMenuContent);

			//Dropdown Button（先解绑再绑定，避免重复）
			$('.mobile-menu').off('click.legacy', 'li.dropdown .dropdown-btn');
			$('.mobile-menu').on('click.legacy', 'li.dropdown .dropdown-btn', function() {
				$(this).toggleClass('open');
				$(this).prev('ul').slideToggle(500);
			});

			//Menu Toggle Btn
			$('.mobile-nav-toggler').off('click.legacy');
			$('.mobile-nav-toggler').on('click.legacy', function() {
				$('body').addClass('mobile-menu-visible');
			});

			$('.mobile-menu .menu-backdrop,.mobile-menu .close-btn').off('click.legacy');
			$('.mobile-menu .menu-backdrop,.mobile-menu .close-btn').on('click.legacy', function() {
				$('body').removeClass('mobile-menu-visible');
			});
		}

		if ($('.banner-carousel').length) {
			$('.banner-carousel').trigger('destroy.owl.carousel');
			$('.banner-carousel').owlCarousel({
				loop:true,
				margin:0,
				nav:true,
				smartSpeed: 500,
				autoplay: 6000,
				navText: [ '<span class="fa fa-angle-left"></span>', '<span class="fa fa-angle-right"></span>' ],
				responsive:{
					0:{ items:1 },
					600:{ items:1 },
					800:{ items:1 },
					1024:{ items:1 }
				}
			});
		}

		//Single Item Carousel
		if ($('.single-item-carousel').length) {
			$('.single-item-carousel').trigger('destroy.owl.carousel');
			$('.single-item-carousel').owlCarousel({
				loop:true,
				margin:0,
				nav:true,
				smartSpeed: 500,
				autoplay: 5000,
				navText: [ '<span class="fa fa-angle-left"></span>', '<span class="fa fa-angle-right"></span>' ],
				responsive:{
					0:{ items:1 },
					600:{ items:1 },
					800:{ items:1 },
					1024:{ items:1 }
				}
			});
		}

		// Sponsors Carousel
		if ($('.sponsors-carousel').length) {
			$('.sponsors-carousel').trigger('destroy.owl.carousel');
			$('.sponsors-carousel').owlCarousel({
				loop:true,
				margin:40,
				nav:true,
				smartSpeed: 500,
				autoplay: 4000,
				navText: [ '<span class="fa fa-angle-left"></span>', '<span class="fa fa-angle-right"></span>' ],
				responsive:{
					0:{ items:1 },
					480:{ items:2 },
					600:{ items:3 },
					800:{ items:4 },
					1024:{ items:5 }
				}
			});
		}

		// Awards Carousel
		if ($('.awards-carousel').length) {
			$('.awards-carousel').trigger('destroy.owl.carousel');
			$('.awards-carousel').owlCarousel({
				loop:true,
				margin:40,
				nav:true,
				smartSpeed: 500,
				autoplay: 4000,
				navText: [ '<span class="fa fa-angle-left"></span>', '<span class="fa fa-angle-right"></span>' ],
				responsive:{
					0:{ items:2 },
					480:{ items:2 },
					600:{ items:3 },
					800:{ items:4 },
					1024:{ items:5 }
				}
			});
		}

		//Tabs Box
		if($('.tabs-box').length){
			$('.tabs-box .tab-buttons .tab-btn').off('click.legacy');
			$('.tabs-box .tab-buttons .tab-btn').on('click.legacy', function(e) {
				e.preventDefault();
				var target = $($(this).attr('data-tab'));

				if ($(target).is(':visible')){
					return false;
				}else{
					target.parents('.tabs-box').find('.tab-buttons').find('.tab-btn').removeClass('active-btn');
					$(this).addClass('active-btn');
					target.parents('.tabs-box').find('.tabs-content').find('.tab').fadeOut(0);
					target.parents('.tabs-box').find('.tabs-content').find('.tab').removeClass('active-tab');
					$(target).fadeIn(300);
					$(target).addClass('active-tab');
				}
			});
		}

		//LightBox / Fancybox
		if($('.lightbox-image').length) {
			try {
				$('.lightbox-image').fancybox({
					openEffect  : 'fade',
					closeEffect : 'fade',
					helpers : {
						media : {}
					}
				});
			} catch (e) {
				// ignore
			}
		}

		//Contact Form Validation
		if($('#contact-form').length){
			try {
				$('#contact-form').validate({
					rules: {
						username: { required: true },
						email: { required: true, email: true },
						message: { required: true }
					}
				});
			} catch (e) {
				// ignore
			}
		}

		// Scroll to a Specific Div
		if($('.scroll-to-target').length){
			$(".scroll-to-target").off('click.legacy');
			$(".scroll-to-target").on('click.legacy', function() {
				var target = $(this).attr('data-target');
				$('html, body').animate({
					scrollTop: $(target).offset().top
				}, 1500);
			});
		}

		// Elements Animation
		if($('.wow').length){
			try {
				// 允许重复 init：创建一个新的 WOW 实例
				var wow = new WOW({
					boxClass:     'wow',
					animateClass: 'animated',
					offset:       0,
					mobile:       false,
					live:         true
				});
				wow.init();
			} catch (e) {
				// ignore
			}
		}

		// 触发一次 headerStyle
		headerStyle();

		// load 时的 preloader 行为（SPA 进入页面也需要模拟）
		handlePreloader();

		// 绑定 scroll（先解绑再绑定）
		$(window).off('scroll.legacy');
		$(window).on('scroll.legacy', function() {
			headerStyle();
		});
	}

	// 暴露给 SPA 调用
	window.__legacyInit = legacyInit;

	// 保持原行为：首次加载时自动初始化
	legacyInit();

})(window.jQuery);
