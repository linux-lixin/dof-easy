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
            <li><a href="#" @click.prevent>Home</a></li>
            <li>Matches</li>
          </ul>
          <h1>Matches</h1>
        </div>
      </div>
    </section>
    <!--End Page Title-->

    <!-- Matches Section -->
    <section class="matches-section">
      <div class="auto-container">
        <!-- Matches Info Tabs-->
        <div class="matches-info-tabs">
          <!-- Matches Tabs-->
          <div class="matches-tabs tabs-box">
            <!--Tab Btns-->
            <ul class="tab-btns tab-buttons clearfix">
              <li data-tab="#prod-all" class="tab-btn active-btn"><span>All Matches</span></li>
              <li data-tab="#prod-matches" class="tab-btn"><span>Upcoming matches</span></li>
              <li data-tab="#prod-results" class="tab-btn"><span>recent results</span></li>
            </ul>

            <!--Tabs Container-->
            <div class="tabs-content">
              <!--Tab / Active Tab-->
              <div class="tab active-tab" id="prod-all">
                <div class="content">
                  <!-- Matches Block -->
                  <div class="matches-block">
                    <div class="inner-block">
                      <div class="row clearfix">
                        <!-- Content Column -->
                        <div class="content-column col-lg-7 col-md-12 col-sm-12">
                          <div class="inner-column">
                            <ul class="tags">
                              <li>26 participants</li>
                              <li>Upcoming Matches</li>
                            </ul>
                            <div class="title">Watch Stream</div>
                            <h2><a href="/matches-single">Need for speed Match part 2</a></h2>
                            <div class="date">30th MAY 2019, 11:00 PM</div>
                          </div>
                        </div>

                        <!-- Match Column -->
                        <div class="match-column col-lg-5 col-md-12 col-sm-12">
                          <div class="inner-column">
                            <div class="row clearfix">
                              <!-- Match Item -->
                              <div class="match-item col-lg-6 col-md-6 col-sm-12">
                                <div class="inner-item">
                                  <div class="icon-box"><span class="icon flaticon-lion"></span></div>
                                  <a href="#" class="product" @click.prevent>Loin king</a>
                                </div>
                              </div>
                              <!-- Match Item -->
                              <div class="match-item col-lg-6 col-md-6 col-sm-12">
                                <div class="inner-item">
                                  <div class="icon-box"><span class="icon flaticon-wolf"></span></div>
                                  <a href="#" class="product" @click.prevent>Wolf Smart</a>
                                </div>
                              </div>
                            </div>
                          </div>
                        </div>
                      </div>
                    </div>
                  </div>

                  <!-- Matches Block -->
                  <div class="matches-block">
                    <div class="inner-block">
                      <div class="row clearfix">
                        <!-- Content Column -->
                        <div class="content-column col-lg-7 col-md-12 col-sm-12">
                          <div class="inner-column">
                            <ul class="tags">
                              <li>26 participants</li>
                              <li>Upcoming Matches</li>
                            </ul>
                            <div class="title">Recent Results <span>. 40 : 32</span></div>
                            <h2><a href="/matches-single">BACON TENDERLOIN DRUMSTICK MEATBALL</a></h2>
                            <div class="date">17th APRIL 2019, 11:00 PM</div>
                          </div>
                        </div>

                        <!-- Match Column -->
                        <div class="match-column col-lg-5 col-md-12 col-sm-12">
                          <div class="inner-column">
                            <div class="row clearfix">
                              <!-- Match Item -->
                              <div class="match-item col-lg-6 col-md-6 col-sm-12">
                                <div class="inner-item">
                                  <div class="icon-box"><span class="icon flaticon-wolf"></span></div>
                                  <a href="#" class="product" @click.prevent>Wolf Smart</a>
                                </div>
                              </div>
                              <!-- Match Item -->
                              <div class="match-item col-lg-6 col-md-6 col-sm-12">
                                <div class="inner-item">
                                  <div class="icon-box"><span class="icon flaticon-skull"></span></div>
                                  <a href="#" class="product" @click.prevent>the skull</a>
                                </div>
                              </div>
                            </div>
                          </div>
                        </div>
                      </div>
                    </div>
                  </div>

                  <!-- Matches Block -->
                  <div class="matches-block">
                    <div class="inner-block">
                      <div class="row clearfix">
                        <!-- Content Column -->
                        <div class="content-column col-lg-7 col-md-12 col-sm-12">
                          <div class="inner-column">
                            <ul class="tags">
                              <li>30 participants</li>
                              <li>Upcoming Matches</li>
                            </ul>
                            <div class="title">watch stream</div>
                            <h2><a href="/matches-single">need for speed Matches part 1</a></h2>
                            <div class="date">14th MAY 2019, 11:00 PM</div>
                          </div>
                        </div>

                        <!-- Match Column -->
                        <div class="match-column col-lg-5 col-md-12 col-sm-12">
                          <div class="inner-column">
                            <div class="row clearfix">
                              <!-- Match Item -->
                              <div class="match-item col-lg-6 col-md-6 col-sm-12">
                                <div class="inner-item">
                                  <div class="icon-box"><span class="icon flaticon-playground"></span></div>
                                  <a href="#" class="product" @click.prevent>Monkey</a>
                                </div>
                              </div>
                              <!-- Match Item -->
                              <div class="match-item col-lg-6 col-md-6 col-sm-12">
                                <div class="inner-item">
                                  <div class="icon-box"><span class="icon flaticon-bull"></span></div>
                                  <a href="#" class="product" @click.prevent>Bull Eye</a>
                                </div>
                              </div>
                            </div>
                          </div>
                        </div>
                      </div>
                    </div>
                  </div>

                  <!-- Matches Block -->
                  <div class="matches-block">
                    <div class="inner-block">
                      <div class="row clearfix">
                        <!-- Content Column -->
                        <div class="content-column col-lg-7 col-md-12 col-sm-12">
                          <div class="inner-column">
                            <ul class="tags">
                              <li>28 participants</li>
                              <li>Recent Results</li>
                            </ul>
                            <div class="title">Recent Results <span>. 68 : 36</span></div>
                            <h2><a href="/matches-single">BACON TENDERLOIN DRUMSTICK MEATBALL</a></h2>
                            <div class="date">11th MAY 2019, 11:00 PM</div>
                          </div>
                        </div>

                        <!-- Match Column -->
                        <div class="match-column col-lg-5 col-md-12 col-sm-12">
                          <div class="inner-column">
                            <div class="row clearfix">
                              <!-- Match Item -->
                              <div class="match-item col-lg-6 col-md-6 col-sm-12">
                                <div class="inner-item">
                                  <div class="icon-box"><span class="icon flaticon-lion"></span></div>
                                  <a href="#" class="product" @click.prevent>Loin king</a>
                                </div>
                              </div>
                              <!-- Match Item -->
                              <div class="match-item col-lg-6 col-md-6 col-sm-12">
                                <div class="inner-item">
                                  <div class="icon-box"><span class="icon flaticon-skull"></span></div>
                                  <a href="#" class="product" @click.prevent>the skull</a>
                                </div>
                              </div>
                            </div>
                          </div>
                        </div>
                      </div>
                    </div>
                  </div>

                  <!-- Matches Block -->
                  <div class="matches-block">
                    <div class="inner-block">
                      <div class="row clearfix">
                        <!-- Content Column -->
                        <div class="content-column col-lg-7 col-md-12 col-sm-12">
                          <div class="inner-column">
                            <ul class="tags">
                              <li>18 participants</li>
                              <li>Upcoming Matches</li>
                            </ul>
                            <div class="title">watch stream</div>
                            <h2><a href="/matches-single">Need for Speed Match part 3</a></h2>
                            <div class="date">18th MAY 2019, 11:00 PM</div>
                          </div>
                        </div>

                        <!-- Match Column -->
                        <div class="match-column col-lg-5 col-md-12 col-sm-12">
                          <div class="inner-column">
                            <div class="row clearfix">
                              <!-- Match Item -->
                              <div class="match-item col-lg-6 col-md-6 col-sm-12">
                                <div class="inner-item">
                                  <div class="icon-box"><span class="icon flaticon-portrait"></span></div>
                                  <a href="#" class="product" @click.prevent>The portrait</a>
                                </div>
                              </div>
                              <!-- Match Item -->
                              <div class="match-item col-lg-6 col-md-6 col-sm-12">
                                <div class="inner-item">
                                  <div class="icon-box"><span class="icon flaticon-skull"></span></div>
                                  <a href="#" class="product" @click.prevent>the skull</a>
                                </div>
                              </div>
                            </div>
                          </div>
                        </div>
                      </div>
                    </div>
                  </div>

                  <!-- Matches Block -->
                  <div class="matches-block">
                    <div class="inner-block">
                      <div class="row clearfix">
                        <!-- Content Column -->
                        <div class="content-column col-lg-7 col-md-12 col-sm-12">
                          <div class="inner-column">
                            <ul class="tags">
                              <li>18 participants</li>
                              <li>Recent Results</li>
                            </ul>
                            <div class="title">Recent Results <span>. 38 : 12</span></div>
                            <h2><a href="/matches-single">BACON TENDERLOIN DRUMSTICK MEATBALL</a></h2>
                            <div class="date">10th MAY 2019, 11:00 PM</div>
                          </div>
                        </div>

                        <!-- Match Column -->
                        <div class="match-column col-lg-5 col-md-12 col-sm-12">
                          <div class="inner-column">
                            <div class="row clearfix">
                              <!-- Match Item -->
                              <div class="match-item col-lg-6 col-md-6 col-sm-12">
                                <div class="inner-item">
                                  <div class="icon-box"><span class="icon flaticon-bear"></span></div>
                                  <a href="#" class="product" @click.prevent>Bear</a>
                                </div>
                              </div>
                              <!-- Match Item -->
                              <div class="match-item col-lg-6 col-md-6 col-sm-12">
                                <div class="inner-item">
                                  <div class="icon-box"><span class="icon flaticon-bird"></span></div>
                                  <a href="#" class="product" @click.prevent>Bird</a>
                                </div>
                              </div>
                            </div>
                          </div>
                        </div>
                      </div>
                    </div>
                  </div>

                </div>
              </div>

              <!--Tab-->
              <div class="tab" id="prod-matches">
                <div class="content">
                  <!-- Matches Block -->
                  <div class="matches-block">
                    <div class="inner-block">
                      <div class="row clearfix">
                        <div class="content-column col-lg-7 col-md-12 col-sm-12">
                          <div class="inner-column">
                            <ul class="tags">
                              <li>26 participants</li>
                              <li>Upcoming Matches</li>
                            </ul>
                            <div class="title">Recent Results <span>. 40 : 32</span></div>
                            <h2><a href="/matches-single">BACON TENDERLOIN DRUMSTICK MEATBALL</a></h2>
                            <div class="date">17th APRIL 2019, 11:00 PM</div>
                          </div>
                        </div>

                        <div class="match-column col-lg-5 col-md-12 col-sm-12">
                          <div class="inner-column">
                            <div class="row clearfix">
                              <div class="match-item col-lg-6 col-md-6 col-sm-12">
                                <div class="inner-item">
                                  <div class="icon-box"><span class="icon flaticon-bull"></span></div>
                                  <a href="#" class="product" @click.prevent>bull eye</a>
                                </div>
                              </div>

                              <div class="match-item col-lg-6 col-md-6 col-sm-12">
                                <div class="inner-item">
                                  <div class="icon-box"><span class="icon flaticon-playground"></span></div>
                                  <a href="#" class="product" @click.prevent>bull eye</a>
                                </div>
                              </div>

                            </div>
                          </div>
                        </div>

                      </div>
                    </div>
                  </div>

                  <!-- Matches Block -->
                  <div class="matches-block">
                    <div class="inner-block">
                      <div class="row clearfix">
                        <div class="content-column col-lg-7 col-md-12 col-sm-12">
                          <div class="inner-column">
                            <ul class="tags">
                              <li>26 participants</li>
                              <li>Upcoming Matches</li>
                            </ul>
                            <div class="title">Recent Results</div>
                            <h2><a href="/matches-single">resident evil final chapter battle</a></h2>
                            <div class="date">12th MAY 2019, 11:00 PM</div>
                          </div>
                        </div>

                        <div class="match-column col-lg-5 col-md-12 col-sm-12">
                          <div class="inner-column">
                            <div class="row clearfix">
                              <div class="match-item col-lg-6 col-md-6 col-sm-12">
                                <div class="inner-item">
                                  <div class="icon-box"><span class="icon flaticon-skull"></span></div>
                                  <a href="#" class="product" @click.prevent>skeleton</a>
                                </div>
                              </div>

                              <div class="match-item col-lg-6 col-md-6 col-sm-12">
                                <div class="inner-item">
                                  <div class="icon-box"><span class="icon flaticon-bear"></span></div>
                                  <a href="#" class="product" @click.prevent>bearmen</a>
                                </div>
                              </div>

                            </div>
                          </div>
                        </div>

                      </div>
                    </div>
                  </div>

                  <!-- Matches Block -->
                  <div class="matches-block">
                    <div class="inner-block">
                      <div class="row clearfix">
                        <div class="content-column col-lg-7 col-md-12 col-sm-12">
                          <div class="inner-column">
                            <ul class="tags">
                              <li>26 participants</li>
                              <li>Upcoming Matches</li>
                            </ul>
                            <div class="title">Recent Results <span>. 40 : 32</span></div>
                            <h2><a href="/matches-single">justice league Matches of power</a></h2>
                            <div class="date">18th MAY 2019, 11:00 PM</div>
                          </div>
                        </div>

                        <div class="match-column col-lg-5 col-md-12 col-sm-12">
                          <div class="inner-column">
                            <div class="row clearfix">
                              <div class="match-item col-lg-6 col-md-6 col-sm-12">
                                <div class="inner-item">
                                  <div class="icon-box"><span class="icon flaticon-portrait"></span></div>
                                  <a href="#" class="product" @click.prevent>Cute cat</a>
                                </div>
                              </div>

                              <div class="match-item col-lg-6 col-md-6 col-sm-12">
                                <div class="inner-item">
                                  <div class="icon-box"><span class="icon flaticon-bird"></span></div>
                                  <a href="#" class="product" @click.prevent>black crow</a>
                                </div>
                              </div>

                            </div>
                          </div>
                        </div>

                      </div>
                    </div>
                  </div>

                </div>
              </div>

              <!--Tab-->
              <div class="tab" id="prod-results">
                <div class="content">
                  <!-- Matches Block -->
                  <div class="matches-block">
                    <div class="inner-block">
                      <div class="row clearfix">
                        <div class="content-column col-lg-7 col-md-12 col-sm-12">
                          <div class="inner-column">
                            <ul class="tags">
                              <li>26 participants</li>
                              <li>Upcoming Matches</li>
                            </ul>
                            <div class="title">Recent Results</div>
                            <h2><a href="/matches-single">resident evil final chapter battle</a></h2>
                            <div class="date">12th MAY 2019, 11:00 PM</div>
                          </div>
                        </div>

                        <div class="match-column col-lg-5 col-md-12 col-sm-12">
                          <div class="inner-column">
                            <div class="row clearfix">
                              <div class="match-item col-lg-6 col-md-6 col-sm-12">
                                <div class="inner-item">
                                  <div class="icon-box"><span class="icon flaticon-skull"></span></div>
                                  <a href="#" class="product" @click.prevent>skeleton</a>
                                </div>
                              </div>

                              <div class="match-item col-lg-6 col-md-6 col-sm-12">
                                <div class="inner-item">
                                  <div class="icon-box"><span class="icon flaticon-bear"></span></div>
                                  <a href="#" class="product" @click.prevent>bearmen</a>
                                </div>
                              </div>

                            </div>
                          </div>
                        </div>

                      </div>
                    </div>
                  </div>

                  <!-- Matches Block -->
                  <div class="matches-block">
                    <div class="inner-block">
                      <div class="row clearfix">
                        <div class="content-column col-lg-7 col-md-12 col-sm-12">
                          <div class="inner-column">
                            <ul class="tags">
                              <li>26 participants</li>
                              <li>Upcoming Matches</li>
                            </ul>
                            <div class="title">Recent Results <span>. 40 : 32</span></div>
                            <h2><a href="/matches-single">justice league Matches of power</a></h2>
                            <div class="date">18th MAY 2019, 11:00 PM</div>
                          </div>
                        </div>

                        <div class="match-column col-lg-5 col-md-12 col-sm-12">
                          <div class="inner-column">
                            <div class="row clearfix">
                              <div class="match-item col-lg-6 col-md-6 col-sm-12">
                                <div class="inner-item">
                                  <div class="icon-box"><span class="icon flaticon-portrait"></span></div>
                                  <a href="#" class="product" @click.prevent>Cute cat</a>
                                </div>
                              </div>

                              <div class="match-item col-lg-6 col-md-6 col-sm-12">
                                <div class="inner-item">
                                  <div class="icon-box"><span class="icon flaticon-bird"></span></div>
                                  <a href="#" class="product" @click.prevent>black crow</a>
                                </div>
                              </div>

                            </div>
                          </div>
                        </div>

                      </div>
                    </div>
                  </div>

                </div>
              </div>

            </div>
          </div>
        </div>
      </div>
    </section>
    <!-- End Matches Section -->
  </LegacyLayout>
</template>
