cat("\014")
gc()
rm(list = ls())
gc()
#######
jan_calls <- "/Users/dimbul/Desktop/rddlsim/files/emergency_domain/XYT.csv"
big_calls <-  "/Users/dimbul/Desktop/rddlsim/files/emergency_domain/XYT_Big.csv"
#jan_calls <- 'C:/Users/ashwi/workspace/RDDLv2/files/emergency_domain/XYT.csv'
#big_calls <- 'C:/Users/ashwi/workspace/RDDLv2/files/emergency_domain/XYT_Big.csv'
callFile <- big_calls

calls <- read.csv( callFile )
attach(calls)

X.nz <- X[X!=0]
Y.nz <- Y[X!=0]
Time.nz <- Time[X!=0]
Code.nz <- relevel(Code[X!=0],ref="Code3Med")
gap <- Time.nz[2:length(Time.nz)]-Time.nz[1:length(Time.nz)-1]
gap[gap<0] <- 24+gap[gap<0]
gap <- pmax(0.001,gap)

stopifnot((length(X.nz)==length(Y.nz))&(length(X.nz)==length(Time.nz))&
            (length(X.nz)==length(Code.nz)))

all.data <- data.frame(r=Code.nz,x=X.nz,y=Y.nz,t=Time.nz,g=c(gap,NA))
print( tail(all.data) )

split.index <- floor(length(X.nz)/2)
train.data <- all.data[1:split.index,]
print( table(train.data[,'r'] ) )
print( tail(train.data) )


## Fitting the Distribution. 


hist(all.data$y)
library(MASS)

#Gap Distribution Fitted with Weibull Distribution

number_of_points  <- 10000
gap_values <- all.data$g[complete.cases(all.data$g)]

gap_Distribution_paramters <- fitdistr(gap_values,densfun = dweibull, start = list(scale=1,shape=2))
parameter_values  <- gap_Distribution_paramters$estimate
gap_generated_values <-rweibull(number_of_points ,shape=parameter_values[2],scale = 0.3)
hist(gap_generated_values)


##Fitting a Normal Distribution for the X coordinate 
x_Distribution_parameters <- fitdistr(all.data$x,"normal")
parameter_values_X <- x_Distribution_parameters$estimate
points_generated_X <- rnorm(number_of_points,mean = parameter_values_X[1], sd = parameter_values_X[2] )


##Fitting a Normal Distribution for the Y Coordinate 
y_Distribution_parameters <- fitdistr(all.data$y,"normal")
parameter_values_Y <- y_Distribution_parameters$estimate
point_generated_Y <- rnorm(number_of_points,mean = parameter_values_Y[1], sd = parameter_values_Y[2])

inital_time<- runif(1)
time_values <- c(inital_time)

real_hours <- floor(inital_time)
minutes <- (inital_time - real_hours) * 60
real_minutes <- floor(minutes)
real_seconds <-floor(( minutes - real_minutes)*60)
real_str_time <-format(strptime(paste(real_hours,real_minutes,real_seconds,sep = ":"),format = "%H:%M:%S"), format = "%H:%M:%S %p")
real_time_values <- c(real_str_time)

cur_time<-inital_time
cur_date = initial_date <- as.Date('1/1/2011',format = '%m/%d/%Y')
date_values <- c(cur_date)


for(i in 1:length(gap_generated_values)){
  next_time <- cur_time + gap_generated_values[i]
  if(next_time>24){
    next_time = next_time - 24
    cur_time <- next_time 
    cur_date <- as.Date(cur_date + 1, format = "%Y-%m-%d")
      
      
      
  }
  else{
    cur_time <- next_time 
  }
  if(i!=length(gap_generated_values)){
    
    real_hours <- floor(cur_time)
    minutes <- (cur_time - real_hours) * 60
    real_minutes <- floor(minutes)
    real_seconds <-floor(( minutes - real_minutes)*60)
    real_str_time <-format(strptime(paste(real_hours,real_minutes,real_seconds,sep = ":"),format = "%H:%M:%S"), format = "%H:%M:%S")
    
    
    real_time_values <- c(real_time_values,real_str_time)
    time_values <- c(time_values,cur_time)
    date_values <- c(date_values,cur_date)
  }
  
  
  
  
  
   
 
  
  
  
  
}








test.data <- all.data[1+split.index:dim(all.data)[1]-1,]
print( table(test.data[,'r'] ) )
print( tail(test.data) )

nrow(all.data)
require(earth)
mr <- earth(r~x+y+t,degree=10,nk=40, pmethod="backward",
            data=all.data,trace=3,thresh=0)

new_data <- data.frame(x=points_generated_X, y = point_generated_Y,t = time_values, g = gap_generated_values, date= date_values, time = real_time_values )

predicted_type_emergencies <-predict(mr,newdata = new_data[,c("x","y","t")])

emergency_types <-colnames(predicted_type_emergencies)[apply(predicted_type_emergencies,1,which.max)]

unique(emergency_types)


new_data$r <-emergency_types



new_data$X <- new_data$x * 5291.005617
new_data$Y <- new_data$y * 5291.005617



tail(new_data,10)
new_data <-new_data[,c("r","date","time","X","Y")]
write.csv(new_data,"/Users/dimbul/Desktop/rddlsim/files/emergency_domain/stressed_fitted_model_data_v2.csv")


#training error
# predictions.g <- predict( mg$glm.list[[1]],se.fit = T,
#                           type="response",dispersion = 1/myshape$alpha)
# emp.density <- density(train.data['t'][,1],n=100)
# plot(emp.density$x, emp.density$y, type='l',col='black',ylab='Density',
#      xlab='Time',xlim=c(0,24),main='Arrival of Emergencies (2004-2010)')
# 
# pred.t <- train.data['t'][,1]+predictions.g$fit
# pred.t[pred.t>=24] <- pred.t[pred.t>=24]-24
# pred.density <- density(pred.t,n=100)
# lines(pred.density$x,pred.density$y,col='blue')
# 
# pred.t.upper <- train.data['t'][,1]+predictions.g$fit+4*predictions.g$se.fit
# pred.t.upper[pred.t.upper>=24] <- pred.t.upper[pred.t.upper>=24]-24
# pred.density.upper <- density(pred.t.upper,n=100)
# lines(pred.density.upper$x,pred.density.upper$y,col='green')
# 
# pred.t.lower <- train.data['t'][,1]+predictions.g$fit-4*predictions.g$se.fit
# pred.t.lower[pred.t.lower>=24] <- pred.t.lower[pred.t.lower>=24]-24
# pred.density.lower <- density(pred.t.lower,n=100)
# lines(pred.density.lower$x,pred.density.lower$y,col='red')
# 
# legend('bottom',legend=c('Observed','Predicted','+4SD','-4SD'),cex=0.8,
#        fill=c('black','blue','green','red'))
# 
# train.err <- pred.t[1:length(pred.t)-1]-train.data['t'][,1][2:dim(train.data)[1]]
# print( sum(train.err*train.err) )
# 
# #test error
# predictions.test.g <- predict( mg,newdata = test.data,type="response")
# emp.density <- density(test.data['t'][,1],n=100)
# plot(emp.density$x, emp.density$y, type='l',col='black',ylab='Density',
#      xlab='Time',xlim=c(0,24),main='Arrival of Emergencies (2004-2010)')
# 
# pred.test.t <- test.data['t'][,1]+predictions.test.g
# pred.test.t[pred.test.t>=24] <- pred.test.t[pred.test.t>=24]-24
# pred.test.density <- density(pred.test.t,n=100)
# lines(pred.test.density$x,pred.test.density$y,col='blue')
# 
# legend('bottom',legend=c('Observed','Predicted'),cex=0.8,
#        fill=c('black','blue'))
# 
# test.err <- pred.test.t[1:length(pred.test.t)-1]-test.data['t'][,1][2:dim(test.data)[1]]
# print( sum(test.err*test.err) )


