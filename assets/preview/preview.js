// Finishes the Markdown preview after the body has loaded: typesets maths with
// KaTeX, colours fenced code with highlight.js, draws Mermaid diagrams, swaps
// images that cannot load for a labelled placeholder, and keeps in-page links
// (headings, footnotes) inside the page. Each library is optional: the page only
// includes the ones its body needs.
(function () {
  "use strict";
  var dark = document.documentElement.getAttribute("data-theme") === "dark";

  function each(selector, fn) {
    Array.prototype.forEach.call(document.querySelectorAll(selector), fn);
  }

  if (window.katex) {
    each(".math", function (el) {
      var display = el.classList.contains("math-display");
      var tex = el.textContent;
      try {
        window.katex.render(tex, el, { displayMode: display, throwOnError: false, output: "htmlAndMathml" });
      } catch (e) {
        el.textContent = tex;
        el.classList.add("math-error");
      }
    });
  }

  if (window.hljs) {
    window.hljs.configure({ ignoreUnescapedHTML: true });
    each("pre > code[class*='language-']", function (el) {
      var match = /language-([\w#+.-]+)/.exec(el.className);
      if (match && window.hljs.getLanguage(match[1])) window.hljs.highlightElement(el);
    });
  }

  if (window.mermaid) {
    try {
      window.mermaid.initialize({ startOnLoad: false, securityLevel: "strict", theme: dark ? "dark" : "default" });
      var run = window.mermaid.run({ querySelector: "pre.mermaid" });
      if (run && run.catch) run.catch(function () {});
    } catch (e) {
      // A diagram that fails to parse stays visible as its source text.
    }
  }

  function markMissing(img) {
    if (img.dataset.missing) return;
    img.dataset.missing = "1";
    var label = document.createElement("span");
    label.className = "image-missing";
    var alt = img.getAttribute("alt");
    label.textContent = "Image not available offline" + (alt ? ": " + alt : "");
    img.replaceWith(label);
  }

  each("img", function (img) {
    if (img.complete && img.naturalWidth === 0) markMissing(img);
    else img.addEventListener("error", function () { markMissing(img); });
  });

  document.addEventListener("click", function (event) {
    var link = event.target.closest ? event.target.closest("a[href^='#']") : null;
    if (!link) return;
    event.preventDefault();
    var id = decodeURIComponent(link.getAttribute("href").slice(1));
    var target = id ? document.getElementById(id) || document.getElementsByName(id)[0] : null;
    if (target) target.scrollIntoView({ block: "start" });
    else if (!id) window.scrollTo(0, 0);
  });
})();
