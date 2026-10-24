// ==UserScript==
// @name         Neuro Canvas: now playing
// @namespace    https://github.com/xuilan42/neuro-canvas
// @version      1.0
// @description  Tells the Neuro Canvas mod (on this computer only) what is playing in the browser, so it can show the lyrics in the game.
// @match        https://open.spotify.com/*
// @match        https://music.youtube.com/*
// @match        https://www.youtube.com/*
// @match        https://soundcloud.com/*
// @match        https://music.apple.com/*
// @match        https://www.deezer.com/*
// @match        https://listen.tidal.com/*
// @match        https://music.yandex.ru/*
// @match        https://music.yandex.com/*
// @match        https://vk.com/*
// @grant        GM_xmlhttpRequest
// @connect      127.0.0.1
// ==/UserScript==

(function () {
    'use strict';
    const URL = 'http://127.0.0.1:47821/np';

    function activeMedia() {
        const all = Array.from(document.querySelectorAll('audio, video'));
        return all.find((m) => !m.paused && m.duration > 1) || all.find((m) => m.duration > 1) || all[0] || null;
    }

    function send() {
        const session = navigator.mediaSession;
        const meta = session && session.metadata;
        if (!meta || !meta.title) {
            return;
        }
        const media = activeMedia();
        const art = meta.artwork && meta.artwork.length ? meta.artwork[meta.artwork.length - 1].src : '';
        const payload = {
            title: meta.title,
            artist: meta.artist || '',
            album: meta.album || '',
            art: art,
            position: media ? media.currentTime : null,
            duration: media && isFinite(media.duration) ? media.duration : null,
            playing: media ? !media.paused : session.playbackState === 'playing',
            source: location.hostname
        };
        GM_xmlhttpRequest({
            method: 'POST',
            url: URL,
            data: JSON.stringify(payload),
            headers: { 'Content-Type': 'text/plain' },
            timeout: 1500
        });
    }

    setInterval(send, 1000);
})();
