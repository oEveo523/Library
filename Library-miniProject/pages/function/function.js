// pages/function/function.js
//先引入
import {
    createStoreBindings
} from 'mobx-miniprogram-bindings'
import {
    store
} from '../../store/store'

Page({

    /**
     * 页面的初始数据
     */
    data: {

    },
    bookHandel(e) {
        // console.log(e.currentTarget.dataset.tag);
        let tag = e.currentTarget.dataset.tag;
        // 判断是否登陆
        this.HasLogin();
        // if (!this.HasSchool()) return;
        let url = "/pages/function/" + tag + "/" + tag;

        console.log(url);
        if (url !== "") {
            wx.navigateTo({
                url: url
            })
        }

    },

    /**
     * 生命周期函数--监听页面加载
     */
    onLoad(options) {
        this.storeBindings = createStoreBindings(this, {
            store,
            fields: ['userInfo', 'hasUserInfo', 'hasLogin', 'hasSchool'],
            actions: ['HasLogin', 'HasSchool']
        });
    },

    /**
     * 生命周期函数--监听页面初次渲染完成
     */
    onReady() {

    },

    /**
     * 生命周期函数--监听页面显示
     */
    onShow() {

    },

    /**
     * 生命周期函数--监听页面隐藏
     */
    onHide() {

    },

    /**
     * 生命周期函数--监听页面卸载
     */
    onUnload() {
        this.storeBindings.destroyStoreBindings()
    },

    /**
     * 页面相关事件处理函数--监听用户下拉动作
     */
    onPullDownRefresh() {

    },

    /**
     * 页面上拉触底事件的处理函数
     */
    onReachBottom() {

    },

    /**
     * 用户点击右上角分享
     */
    onShareAppMessage() {

    }
})