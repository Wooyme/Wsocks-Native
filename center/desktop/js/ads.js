
var exoParams = {
    "ad_sub" : (typeof ad_sub !== 'undefined') ? ad_sub : '',
    "ad_sub2" : (typeof ad_sub2 !== 'undefined') ? ad_sub2 : '',
    "ad_sub3" : (typeof ad_sub3 !== 'undefined') ? ad_sub3 : '',
    "ad_tags" : (typeof ad_tags !== 'undefined') ? ad_tags : '',
    "ad_notify" : (typeof ad_notify !== 'undefined') ? ad_notify : '',
    "ad_el" : (typeof ad_el !== 'undefined') ? ad_el : '',
    "ad_width" : (typeof ad_width !== 'undefined') ? ad_width : '',
    "ad_height" : (typeof ad_height !== 'undefined') ? ad_height : ''
};

//EX-1265
var exoDocumentProtocol = (document.location.protocol != "https:" && document.location.protocol != "http:") ? "https:" : document.location.protocol;

(function() {
    //retrieve the parameter "p" from the URL. In case it exists it is assigned
    //to the "p" variable, otherwise, the "p" variables values "document.referrer"
    var p = getParameterFromUrl('p', document.URL);
    if (p === null || p === '') {
        p = (top === self) ? document.URL : document.referrer;
    }

    var dt = new Date().getTime();
    var ad_screen_resolution = screen.width + 'x' + screen.height;
    var ad_type = (exoParams.ad_width === '100%' && exoParams.ad_height === '100%') ? 'auto' : exoParams.ad_width + 'x' + exoParams.ad_height;
    var ad_src = exoDocumentProtocol + '//syndication.exdynsrv.com/ads-iframe-display.php?idzone=' + ad_idzone + '&type=' + ad_type + (exoParams.ad_notify !== "" ? "&notify=" + exoParams.ad_notify : "") + '&p=' + escape(p) + '&dt=' + dt + '&sub=' + exoParams.ad_sub + ((exoParams.ad_sub2 !== "") ? "&sub2=" + exoParams.ad_sub2 : "") + ((exoParams.ad_sub3 !== "") ? "&sub3=" + exoParams.ad_sub3 : "") + '&tags=' + exoParams.ad_tags + '&screen_resolution=' + ad_screen_resolution + '&el=' + exoParams.ad_el + '" ';
    var ad_frame = createFrame(ad_src, exoParams.ad_width, exoParams.ad_height);
    document.write(ad_frame);

    function createFrame(ad_src, ad_width, ad_height) {
        ad_frame = document.createElement("iframe");
        ad_frame.setAttribute('src', ad_src);
        ad_frame.setAttribute('width', ad_width);
        ad_frame.setAttribute('height', ad_height);
        ad_frame.setAttribute('sandbox', 'allow-forms allow-pointer-lock allow-popups allow-popups-to-escape-sandbox allow-same-origin allow-scripts');
        ad_frame.setAttribute('frameborder', '0');
        ad_frame.setAttribute('scrolling', 'no');
        ad_frame.setAttribute('marginwidth', '0');
        ad_frame.setAttribute('marginheight', '0');
        return ad_frame.outerHTML;
    }

    function getParameterFromUrl(name, url) {
        name = name.replace(/[\[\]]/g, '\\$&');
        var regex = new RegExp('[?&]' + name + '(=([^&#]*)|&|#|$)'),
            results = regex.exec(url);
        if (!results) return null;
        if (!results[2]) return '';
        return decodeURIComponent(results[2].replace(/\+/g, ' '));
    }
}(exoParams));
