<script setup lang="ts">
import LegacyLayout from './LegacyLayout.vue';
import { useLegacyScripts } from '../../../composables/legacy/useLegacyScripts';

useLegacyScripts(true);
</script>

<template>
  <LegacyLayout>
    <!--Page Title-->
    <section class="page-banner" style="background-image:url(/assets/legacy/images/background/title-bg.jpg);">
      <div class="auto-container">
        <div class="inner-container clearfix">
          <ul class="bread-crumb clearfix">
            <li><a href="/home">Home</a></li>
            <li>Blog</li>
          </ul>
          <h1>Blog</h1>
        </div>
      </div>
    </section>
    <!--End Page Title-->

    <!--Sidebar Page Container-->
    <div class="sidebar-page-container">
      <div class="auto-container">
        <div class="row clearfix">
          <!--Content Side / Blog Detail-->
          <div class="content-side col-lg-8 col-md-12 col-sm-12">
            <div class="blog-detail">
              <div class="image-box">
                <figure class="image"><img src="/assets/legacy/images/resource/news-single.jpg" alt="" /></figure>
              </div>
              <div class="lower-content">
                <div class="post-date">18 may 2019</div>
                <h3>what happened to esports games this weekend</h3>
                <ul class="post-info">
                  <li>by <a href="#" @click.prevent>Gamon</a></li>
                  <li><a href="#" @click.prevent>2 Comments</a></li>
                  <li><a href="#" @click.prevent>15 Likes</a></li>
                </ul>
                <p>
                  There are many variations of passages of lorem ipsum available, but the majority have suffered
                  alteration in some form, by injected humour, or simply free textinh randomised words which don't look
                  even slightly believable. If you are going to use a passage of Lorem Ipsum, you need to be sure there
                  isn't anything embarrassing hidden in the middle of text. Aelltes port lacus quis enim ipsu var sed
                  efficitur turpis gilla sed sit lorem ipsum is simply free text available amet finibus eros. Lorem
                  ipsum is simply free text used by copytyping refreshing. Neque porro est qui dolorem ipsum quia quaed
                  inventore veritatis et quasi architecto beatae vitae dicta sunt explicabo. Lorem is simply free text
                  used by copytyping refreshing. Neque porro est qui dolorem ipsum quia quaed inventore veritatis et
                  quasi architecto beatae vitae dicta sunt explicabo. Aelltes port lacus quis enim var sed efficitur
                  turpis gilla sed sit amet finibus eros. Lorem Ipsum is simply dummy text of the printing and
                  typesetting industry. Lorem Ipsum has been the ndustry standard dummy text ever since the 1500s, when
                  an unknown printer took a galley of type and scrambled it to make a type specimen book. It has
                  survived not only five centuries. Lorem Ipsum is simply dummy text of the new design printng and type
                  setting Ipsum Take a look at our round up of the best shows coming soon to your telly box has been
                  the is industrys. Lorem Ipsum is simply dummy text of the printing and typesetting industry. Lorem
                  Ipsum has industr standard dummy text ever since the 1500s, when an unknown printer took a galley of
                  type and scrambled it to make a type specimen book. It has survived not only five centuries, but also
                  the leap into electronic typesetting, remaining essentially unchanged.
                </p>
              </div>
            </div>

            <div class="post-share-options clearfix">
              <div class="pull-left">
                <ul class="tags">
                  <li><a href="#" @click.prevent>Gaming</a></li>
                  <li><a href="/matches">Matches</a></li>
                  <li><a href="#" @click.prevent>Players</a></li>
                </ul>
              </div>

              <div class="social-links pull-right">
                <ul class="social-links">
                  <li><a href="#" @click.prevent><span class="fab fa-twitter"></span></a></li>
                  <li><a href="#" @click.prevent><span class="fab fa-facebook-square"></span></a></li>
                  <li><a href="#" @click.prevent><span class="fab fa-pinterest-p"></span></a></li>
                  <li><a href="#" @click.prevent><span class="fab fa-instagram"></span></a></li>
                </ul>
              </div>
            </div>

            <div class="author-box">
              <div class="inner-box">
                <figure class="thumb"><img src="/assets/legacy/images/resource/author-thumb.jpg" alt="" /></figure>
                <h3 class="name">Christine Eve</h3>
                <div class="text">
                  Neque porro est qui dolorem ipsum quia quaed inventore veritatis et quasi architecto beatae dicta sunt
                  explica lltes port lacus quis enim var sed turpis gilla sed.
                </div>
              </div>
            </div>

            <div class="comments-area">
              <div class="group-title"><h3>2 Comments</h3></div>
              <div class="comment-box">
                <div class="comment">
                  <div class="author-thumb">
                    <figure class="thumb"><img src="/assets/legacy/images/resource/thumb-1.jpg" alt="" /></figure>
                  </div>
                  <h4 class="name">Kevin Martin</h4>
                  <div class="text">
                    Lorem ipsum is simply free text used by copytyping refreshing. Neque porro est qui dolorem ipsum
                    quia quaed inventore veritatis et quasi architecto beatae vitae dicta sunt explicabo var sed
                    efficitur turpis gilla sed sit amet finibus eros.
                  </div>
                  <a href="#" class="reply-btn" @click.prevent>Reply</a>
                </div>
              </div>

              <div class="comment-box reply-comment">
                <div class="comment">
                  <div class="author-thumb">
                    <figure class="thumb"><img src="/assets/legacy/images/resource/thumb-2.jpg" alt="" /></figure>
                  </div>
                  <h4 class="name">Sarah Albert</h4>
                  <div class="text">
                    Lorem ipsum is simply free text used by copytyping refreshing. Neque porro est qui dolorem ipsum
                    quia quaed inventore veritatis et quasi architecto beatae vitae dicta sunt explicabo var sed
                    efficitur turpis gilla sed sit amet finibus eros.
                  </div>
                  <a href="#" class="reply-btn" @click.prevent>Reply</a>
                </div>
              </div>
            </div>

            <div class="comment-form">
              <div class="group-title"><h3>Leave a comment</h3></div>
              <form method="post" action="/blog" @submit.prevent>
                <div class="row clearfix">
                  <div class="col-md-6 col-sm-12 form-group">
                    <input type="text" name="username" placeholder="Full name" required />
                  </div>
                  <div class="col-md-6 col-sm-12 form-group">
                    <input type="email" name="email" placeholder="Email address" required />
                  </div>
                  <div class="col-md-12 col-sm-12 form-group">
                    <textarea name="message" placeholder="Write a comment"></textarea>
                  </div>
                  <div class="col-md-12 col-sm-12 form-group">
                    <button class="theme-btn btn-style-one" type="submit" name="submit-form">
                      <span class="btn-title">Post Comment</span>
                    </button>
                  </div>
                </div>
              </form>
            </div>
          </div>

          <!--Sidebar Side-->
          <div class="sidebar-side col-lg-4 col-md-12 col-sm-12">
            <aside class="sidebar">
              <div class="sidebar-widget search-box">
                <form method="post" action="/contact" @submit.prevent>
                  <div class="form-group">
                    <input type="search" name="search-field" value="" placeholder="Search" required />
                    <button type="submit"><span class="icon fa fa-search"></span></button>
                  </div>
                </form>
              </div>

              <div class="sidebar-widget popular-posts">
                <div class="widget-content">
                  <h4 class="sidebar-title">Recent Posts</h4>
                  <article class="post">
                    <div class="post-inner">
                      <figure class="post-thumb">
                        <a href="/blog-single"><img src="/assets/legacy/images/resource/post-thumb-1.jpg" alt="" /></a>
                      </figure>
                      <div class="post-info">16 May 2019</div>
                      <div class="text"><a href="/blog-single">Lion king vs wolf smart upcoming matches</a></div>
                    </div>
                  </article>

                  <article class="post">
                    <div class="post-inner">
                      <figure class="post-thumb">
                        <a href="/blog-single"><img src="/assets/legacy/images/resource/post-thumb-2.jpg" alt="" /></a>
                      </figure>
                      <div class="post-info">11 May 2019</div>
                      <div class="text"><a href="/blog-single">How to become a register user on gamon</a></div>
                    </div>
                  </article>

                  <article class="post">
                    <div class="post-inner">
                      <figure class="post-thumb">
                        <a href="/blog-single"><img src="/assets/legacy/images/resource/post-thumb-3.jpg" alt="" /></a>
                      </figure>
                      <div class="post-info">10 May 2019</div>
                      <div class="text"><a href="/blog-single">Last matches champions advices for</a></div>
                    </div>
                  </article>
                </div>
              </div>

              <div class="sidebar-widget popular-tags">
                <div class="widget-content">
                  <h4 class="sidebar-title">popular tags</h4>
                  <a href="/matches">Matches</a>
                  <a href="#" @click.prevent>tournaments</a>
                  <a href="#" @click.prevent>champions</a>
                  <a href="#" @click.prevent>Players</a>
                  <a href="#" @click.prevent>Game</a>
                  <a href="#" @click.prevent>esport</a>
                  <a href="#" @click.prevent>video</a>
                </div>
              </div>

              <div class="sidebar-widget categories">
                <div class="widget-content">
                  <h4 class="sidebar-title">Categories</h4>
                  <ul class="blog-categories">
                    <li><a href="/blog-single">Gaming</a></li>
                    <li><a href="/blog-single">Matches</a></li>
                    <li><a href="/blog-single">Video games</a></li>
                    <li><a href="/blog-single">Esports</a></li>
                    <li><a href="/blog-single">Champions</a></li>
                  </ul>
                </div>
              </div>
            </aside>
          </div>
        </div>
      </div>
    </div>
    <!-- End Sidebar Page Container -->
  </LegacyLayout>
</template>
