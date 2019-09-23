
import { NativeModules,NativeEventEmitter } from 'react-native';

const { RNIDCardReader } = NativeModules;

export default {
	start:function(){
		RNIDCardReader.start();
	},
	stop:function(){
		RNIDCardReader.stop();
	},
	on:function(fn){
		const eventEmitter = new NativeEventEmitter(RNIDCardReader);
		eventEmitter.addListener('callback', (event) => {
		   fn(event)
		});
	}
};
